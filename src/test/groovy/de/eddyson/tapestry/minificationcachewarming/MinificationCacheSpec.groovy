package de.eddyson.tapestry.minificationcachewarming

import groovy.mock.interceptor.MockFor;

import org.apache.tapestry5.internal.services.ContextImpl;
import org.apache.tapestry5.internal.services.assets.JavaScriptStackAssembler;
import org.apache.tapestry5.ioc.MappedConfiguration;
import org.apache.tapestry5.ioc.OrderedConfiguration;
import org.apache.tapestry5.ioc.annotations.Contribute;
import org.apache.tapestry5.ioc.annotations.Inject;
import org.apache.tapestry5.ioc.annotations.Startup;
import org.apache.tapestry5.ioc.annotations.SubModule;
import org.apache.tapestry5.ioc.internal.services.RegistryStartup;
import org.apache.tapestry5.ioc.services.ApplicationDefaults;
import org.apache.tapestry5.ioc.services.SymbolProvider;
import org.apache.tapestry5.modules.TapestryModule
import org.apache.tapestry5.services.ApplicationGlobals
import org.apache.tapestry5.services.AssetSource
import org.apache.tapestry5.services.Context
import org.apache.tapestry5.services.Request;
import org.apache.tapestry5.services.RequestGlobals;
import org.apache.tapestry5.services.javascript.JavaScriptAggregationStrategy;
import org.apache.tapestry5.services.javascript.StackExtension;

import de.eddyson.tapestry.minificationcachewarming.MinificationCacheWarmingModule;
import de.eddyson.tapestry.minificationcachewarming.services.MinificationCacheWarming;

@SubModule([TapestryModule, TestModule, MinificationCacheWarmingModule])
class MinificationCacheSpec extends spock.lang.Specification {

 @Inject
 private JavaScriptStackAssembler javaScriptStackAssembler
 
 @Inject
 private RequestGlobals requestGlobals
 
 
  def "Retrieve a minified stack"(){
    
    setup:
    Request request = Mock()
    requestGlobals.storeRequestResponse(request, null)
    
    
    when:
    def stack = javaScriptStackAssembler.assembleJavaScriptResourceForStack("core", false, JavaScriptAggregationStrategy.COMBINE_AND_MINIMIZE)
    then:
    stack != null
   
  }

 
  public static class TestModule {
    
    @Contribute(Runnable)
    public static void storeContext(ApplicationGlobals applicationGlobals, OrderedConfiguration<Runnable> configuration){
      configuration.add("StoreContext", {
        applicationGlobals.storeContext(new ContextImpl(null))
      }, "before:MinificationCacheWarming")
    }
    
    @Contribute(SymbolProvider)
    @ApplicationDefaults
    public static void addSymbols(MappedConfiguration<String, Object> configuration){
      configuration.with {
        add('tapestry.app-name', 'TestApp')
        add('tapestry.app-package', 'de.eddyson.testapp')
        add('tapestry.supported-locales', 'en')
      }
    }
    
    @Contribute(MinificationCacheWarming.class)
    public static void configureMinificationCacheWarming(final OrderedConfiguration<StackExtension> configuration) {
      configuration.add("stack:core", StackExtension.stack("core"))
      configuration.add("module:bootstrap/modal", StackExtension.module("bootstrap/modal"))
      configuration.add("stylesheet:tapestry.css", StackExtension.stylesheet('${tapestry.asset.root}/tapestry.css'))
    }
    
  }
   
}
