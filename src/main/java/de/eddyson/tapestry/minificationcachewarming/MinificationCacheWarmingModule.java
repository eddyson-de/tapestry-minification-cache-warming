package de.eddyson.tapestry.minificationcachewarming;

import java.lang.reflect.Method;

import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.ioc.MethodAdviceReceiver;
import org.apache.tapestry5.ioc.OrderedConfiguration;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Advise;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.plastic.MethodAdvice;
import org.apache.tapestry5.plastic.MethodInvocation;
import org.apache.tapestry5.services.assets.ResourceMinimizer;
import org.apache.tapestry5.services.assets.StreamableResource;
import org.slf4j.Logger;

import de.eddyson.tapestry.minificationcachewarming.services.MinificationCacheWarming;
import de.eddyson.tapestry.minificationcachewarming.services.MinificationCacheWarmingImpl;

public final class MinificationCacheWarmingModule {

  // TODO add symbol
  private final static long slowMinificationThresholdMillis = 300l;

  private static boolean startupComplete = false;

  public static void bind(final ServiceBinder binder) {
    binder.bind(MinificationCacheWarming.class, MinificationCacheWarmingImpl.class);
  }

  @Contribute(Runnable.class)
  public static void warmMinifiedJavascriptCache(final MinificationCacheWarming minificationCacheWarming,
      @Symbol(SymbolConstants.MINIFICATION_ENABLED) final boolean minificationEnabled,
      final OrderedConfiguration<Runnable> configuration) {

    // TODO add separate symbol defaulting to
    // SymbolConstants.MINIFICATION_ENABLED
    if (minificationEnabled) {
      configuration.add("MinificationCacheWarming", new Runnable() {

        @Override
        public void run() {
          try {
            minificationCacheWarming.warmMinificationCache();
          } catch (Exception e) {
            throw new RuntimeException("Error warming minification cache", e);
          }
        }
      });
      startupComplete = true;
    }
  }

  @Advise(serviceInterface = ResourceMinimizer.class)
  public static void logSlowMinification(final MethodAdviceReceiver adviceReceiver, final Logger logger)
      throws NoSuchMethodException, SecurityException {
    // TODO add symbol to toggle logging behavior
    @SuppressWarnings("unchecked")
    Method m = adviceReceiver.getInterface().getMethod("minimize", StreamableResource.class);

    adviceReceiver.adviseMethod(m, new MethodAdvice() {

      @Override
      public void advise(final MethodInvocation invocation) {

        if (!startupComplete) {
          invocation.proceed();
        } else {
          long start = System.currentTimeMillis();
          invocation.proceed();
          long end = System.currentTimeMillis();
          if (end - start >= slowMinificationThresholdMillis) {
            logger.warn("Minification of {} took {} ms, consider adding it to the cache warming process.",
                invocation.getParameter(0), end - start);
          }
        }
      }
    });
  }

  private MinificationCacheWarmingModule() {
  }

}
