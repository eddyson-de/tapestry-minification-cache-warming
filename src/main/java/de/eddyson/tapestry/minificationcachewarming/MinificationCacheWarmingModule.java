package de.eddyson.tapestry.minificationcachewarming;

import de.eddyson.tapestry.minificationcachewarming.services.MinificationCacheWarming;
import de.eddyson.tapestry.minificationcachewarming.services.MinificationCacheWarmingImpl;
import org.apache.tapestry5.SymbolConstants;
import org.apache.tapestry5.commons.MappedConfiguration;
import org.apache.tapestry5.commons.OrderedConfiguration;
import org.apache.tapestry5.ioc.MethodAdviceReceiver;
import org.apache.tapestry5.ioc.ServiceBinder;
import org.apache.tapestry5.ioc.annotations.Advise;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Symbol;
import org.apache.tapestry5.ioc.services.FactoryDefaults;
import org.apache.tapestry5.ioc.services.SymbolProvider;
import org.apache.tapestry5.plastic.MethodAdvice;
import org.apache.tapestry5.plastic.MethodInvocation;
import org.apache.tapestry5.services.assets.ResourceMinimizer;
import org.apache.tapestry5.services.assets.StreamableResource;
import org.slf4j.Logger;

import java.lang.reflect.Method;

public final class MinificationCacheWarmingModule {

  // TODO add symbol
  private final static long slowMinificationThresholdMillis = 300l;

  private static boolean startupComplete = false;

  public static void bind(final ServiceBinder binder) {
    binder.bind(MinificationCacheWarming.class, MinificationCacheWarmingImpl.class);
  }

  @Contribute(SymbolProvider.class)
  @FactoryDefaults
  public static void addDefaultConfiguration(final MappedConfiguration<String, Object> configuration) {
    configuration.add(MinificationCacheWarmingSymbols.ENABLE_MINIFICATION_CACHE_WARMING, "${"
        + SymbolConstants.MINIFICATION_ENABLED + "}");
    configuration.add(MinificationCacheWarmingSymbols.LOG_SLOW_MINIFICATION, Boolean.TRUE);
  }

  @Contribute(Runnable.class)
  public static void warmMinifiedJavascriptCache(
      final MinificationCacheWarming minificationCacheWarming,
      @Symbol(MinificationCacheWarmingSymbols.ENABLE_MINIFICATION_CACHE_WARMING) final boolean minificationCacheWarmingEnabled,
      final OrderedConfiguration<Runnable> configuration) {

    if (minificationCacheWarmingEnabled) {
      configuration.add("MinificationCacheWarming", new Runnable() {

        @Override
        public void run() {
          try {
            minificationCacheWarming.warmMinificationCache();
          } catch (Exception e) {
            throw new RuntimeException("Error warming minification cache", e);
          }
          startupComplete = true;
        }
      });
    }
  }

  @Advise(serviceInterface = ResourceMinimizer.class)
  public static void logSlowMinification(final MethodAdviceReceiver adviceReceiver, final Logger logger,
      @Symbol(MinificationCacheWarmingSymbols.LOG_SLOW_MINIFICATION) final boolean logSlowMinification)
      throws NoSuchMethodException, SecurityException {
    if (logSlowMinification) {
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
  }

  private MinificationCacheWarmingModule() {
  }

}
