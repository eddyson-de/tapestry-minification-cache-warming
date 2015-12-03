package de.eddyson.tapestry.minificationcachewarming.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.tapestry5.internal.services.assets.JavaScriptStackAssembler;
import org.apache.tapestry5.internal.services.assets.ResourceChangeTracker;
import org.apache.tapestry5.ioc.Invokable;
import org.apache.tapestry5.ioc.Resource;
import org.apache.tapestry5.ioc.internal.util.CollectionFactory;
import org.apache.tapestry5.ioc.services.ParallelExecutor;
import org.apache.tapestry5.ioc.services.ThreadLocale;
import org.apache.tapestry5.services.AssetSource;
import org.apache.tapestry5.services.LocalizationSetter;
import org.apache.tapestry5.services.Request;
import org.apache.tapestry5.services.RequestGlobals;
import org.apache.tapestry5.services.Session;
import org.apache.tapestry5.services.assets.StreamableResourceProcessing;
import org.apache.tapestry5.services.assets.StreamableResourceSource;
import org.apache.tapestry5.services.javascript.JavaScriptAggregationStrategy;
import org.apache.tapestry5.services.javascript.ModuleManager;
import org.apache.tapestry5.services.javascript.StackExtension;
import org.slf4j.Logger;

public class MinificationCacheWarmingImpl implements MinificationCacheWarming {

  private final List<String> modules = CollectionFactory.newList();
  private final List<String> stacks = CollectionFactory.newList();
  private final List<String> stylesheets = CollectionFactory.newList();
  private final ModuleManager moduleManager;
  private final StreamableResourceSource streamableResourceSource;
  private final ResourceChangeTracker resourceChangeTracker;
  private final JavaScriptStackAssembler javaScriptStackAssembler;
  private final RequestGlobals requestGlobals;
  private final LocalizationSetter localizationSetter;
  private final ThreadLocale threadLocale;
  private final ParallelExecutor parallelExecutor;
  private final Logger logger;
  private final AssetSource assetSource;

  public MinificationCacheWarmingImpl(final List<StackExtension> entries, final ModuleManager moduleManager,
      final StreamableResourceSource streamableResourceSource, final ResourceChangeTracker resourceChangeTracker,
      final JavaScriptStackAssembler javaScriptStackAssembler, final RequestGlobals requestGlobals,
      final LocalizationSetter localizationSetter, final ThreadLocale threadLocale,
      final ParallelExecutor parallelExecutor, final Logger logger, final AssetSource assetSource) {
    this.moduleManager = moduleManager;
    this.streamableResourceSource = streamableResourceSource;
    this.resourceChangeTracker = resourceChangeTracker;
    this.javaScriptStackAssembler = javaScriptStackAssembler;
    this.requestGlobals = requestGlobals;
    this.localizationSetter = localizationSetter;
    this.threadLocale = threadLocale;
    this.parallelExecutor = parallelExecutor;
    this.logger = logger;
    this.assetSource = assetSource;

    for (StackExtension stackExtension : entries) {
      switch (stackExtension.type) {
      case MODULE:
        modules.add(stackExtension.value);
        break;
      case STACK:
        stacks.add(stackExtension.value);
        break;
      case STYLESHEET:
        stylesheets.add(stackExtension.value);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported stack extension type " + stackExtension.type);
      }

    }

  }

  @Override
  public void warmMinificationCache() throws InterruptedException, ExecutionException {
    List<Resource> resources = new ArrayList<Resource>(modules.size() + stylesheets.size());

    for (String module : modules) {
      Resource resource = moduleManager.findResourceForModule(module);
      if (resource == null) {
        throw new RuntimeException("Resource not found for module " + module);
      }
      resources.add(resource);
    }
    for (String stylesheet : stylesheets) {
      resources.add(assetSource.getExpandedAsset(stylesheet).getResource());
    }

    Set<Future<?>> futures = CollectionFactory.newSet();

    for (final Locale locale : localizationSetter.getSupportedLocales()) {
      for (final String stack : stacks) {
        futures.add(parallelExecutor.invoke(new MinifyStack(locale, stack)));
      }
    }

    for (final Resource resource : resources) {
      futures.add(parallelExecutor.invoke(new MinifyResource(resource)));

    }
    for (Future<?> future : futures) {
      future.get();
    }

  }

  private final class MinifyResource implements Invokable<Void> {
    private final Resource resource;

    MinifyResource(final Resource resource) {
      this.resource = resource;
    }

    @Override
    public Void invoke() {
      logger.debug("Requesting minified resource {}", resource);
      try {
        // needed by JavaScriptStackMinimizeDisabler.getStreamableResource
        requestGlobals.storeRequestResponse(new NoOpRequest(), null);
        // needed by ResponseCompressionAnalyzerImpl.isGZipSupported
        requestGlobals.storeServletRequestResponse(new NoOpServletRequest(), null);

        streamableResourceSource.getStreamableResource(resource, StreamableResourceProcessing.COMPRESSION_DISABLED,
            resourceChangeTracker);
      } catch (Exception e) {
        logger.error("Error requesting minified resource {}", resource, e);
      }
      return null;

    }
  }

  private final class MinifyStack implements Invokable<Void> {
    private final Locale locale;
    private final String stack;

    MinifyStack(final Locale locale, final String stack) {
      this.locale = locale;
      this.stack = stack;
    }

    @Override
    public Void invoke() {
      logger.debug("Requesting minified stack {} for locale {}", stack, locale);

      threadLocale.setLocale(locale);

      try {
        // needed by JavaScriptStackMinimizeDisabler.getStreamableResource
        requestGlobals.storeRequestResponse(new NoOpRequest(), null);
        // needed by ResponseCompressionAnalyzerImpl.isGZipSupported
        requestGlobals.storeServletRequestResponse(new NoOpServletRequest(), null);

        javaScriptStackAssembler.assembleJavaScriptResourceForStack(stack, false,
            JavaScriptAggregationStrategy.COMBINE_AND_MINIMIZE);
      } catch (Exception e) {
        logger.error("Error requesting minified stack {} for locale {}", stack, locale, e);
      }
      return null;
    }
  }

  private static final class NoOpRequest implements Request {

    @Override
    public Session getSession(final boolean create) {
      return null;
    }

    @Override
    public String getContextPath() {
      return null;
    }

    @Override
    public List<String> getParameterNames() {
      return null;
    }

    @Override
    public String getParameter(final String name) {
      return null;
    }

    @Override
    public String[] getParameters(final String name) {
      return null;
    }

    @Override
    public String getPath() {
      return null;
    }

    @Override
    public Locale getLocale() {
      return null;
    }

    @Override
    public List<String> getHeaderNames() {
      return null;
    }

    @Override
    public long getDateHeader(final String name) {
      return 0;
    }

    @Override
    public String getHeader(final String name) {
      return null;
    }

    @Override
    public boolean isXHR() {
      return false;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public String getServerName() {
      return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
      return false;
    }

    @Override
    public Object getAttribute(final String name) {
      return null;
    }

    @Override
    public List<String> getAttributeNames() {
      return null;
    }

    @Override
    public void setAttribute(final String name, final Object value) {
    }

    @Override
    public String getMethod() {
      return null;
    }

    @Override
    public int getLocalPort() {
      return 0;
    }

    @Override
    public int getServerPort() {
      return 0;
    }

    @Override
    public String getRemoteHost() {
      return null;
    }

    @Override
    public boolean isSessionInvalidated() {
      return false;
    }

  }

  @SuppressWarnings("rawtypes")
  private static final class NoOpServletRequest implements HttpServletRequest {

    @Override
    public Object getAttribute(final String name) {
      return null;
    }

    @Override
    public Enumeration getAttributeNames() {
      return null;
    }

    @Override
    public String getCharacterEncoding() {
      return null;
    }

    @Override
    public void setCharacterEncoding(final String env) throws UnsupportedEncodingException {

    }

    @Override
    public int getContentLength() {
      return 0;
    }

    @Override
    public String getContentType() {
      return null;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      return null;
    }

    @Override
    public String getParameter(final String name) {
      return null;
    }

    @Override
    public Enumeration getParameterNames() {
      return null;
    }

    @Override
    public String[] getParameterValues(final String name) {
      return null;
    }

    @Override
    public Map getParameterMap() {
      return null;
    }

    @Override
    public String getProtocol() {
      return "HTTP/1.0";
    }

    @Override
    public String getScheme() {
      return null;
    }

    @Override
    public String getServerName() {
      return null;
    }

    @Override
    public int getServerPort() {
      return 0;
    }

    @Override
    public BufferedReader getReader() throws IOException {
      return null;
    }

    @Override
    public String getRemoteAddr() {
      return null;
    }

    @Override
    public String getRemoteHost() {
      return null;
    }

    @Override
    public void setAttribute(final String name, final Object o) {

    }

    @Override
    public void removeAttribute(final String name) {

    }

    @Override
    public Locale getLocale() {
      return null;
    }

    @Override
    public Enumeration getLocales() {
      return null;
    }

    @Override
    public boolean isSecure() {
      return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
      return null;
    }

    @Override
    public String getRealPath(final String path) {
      return null;
    }

    @Override
    public int getRemotePort() {
      return 0;
    }

    @Override
    public String getLocalName() {
      return null;
    }

    @Override
    public String getLocalAddr() {
      return null;
    }

    @Override
    public int getLocalPort() {
      return 0;
    }

    @Override
    public String getAuthType() {
      return null;
    }

    @Override
    public Cookie[] getCookies() {
      return null;
    }

    @Override
    public long getDateHeader(final String name) {
      return 0;
    }

    @Override
    public String getHeader(final String name) {
      return null;
    }

    @Override
    public Enumeration getHeaders(final String name) {
      return null;
    }

    @Override
    public Enumeration getHeaderNames() {
      return null;
    }

    @Override
    public int getIntHeader(final String name) {
      return 0;
    }

    @Override
    public String getMethod() {
      return null;
    }

    @Override
    public String getPathInfo() {
      return null;
    }

    @Override
    public String getPathTranslated() {
      return null;
    }

    @Override
    public String getContextPath() {
      return null;
    }

    @Override
    public String getQueryString() {
      return null;
    }

    @Override
    public String getRemoteUser() {
      return null;
    }

    @Override
    public boolean isUserInRole(final String role) {
      return false;
    }

    @Override
    public Principal getUserPrincipal() {
      return null;
    }

    @Override
    public String getRequestedSessionId() {
      return null;
    }

    @Override
    public String getRequestURI() {
      return null;
    }

    @Override
    public StringBuffer getRequestURL() {
      return null;
    }

    @Override
    public String getServletPath() {
      return null;
    }

    @Override
    public HttpSession getSession(final boolean create) {
      return null;
    }

    @Override
    public HttpSession getSession() {
      return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
      return false;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
      return false;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
      return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
      return false;
    }

  }

}
