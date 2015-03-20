package de.eddyson.tapestry.minificationcachewarming.services;

import java.util.concurrent.ExecutionException;

import org.apache.tapestry5.ioc.annotations.UsesOrderedConfiguration;
import org.apache.tapestry5.services.javascript.StackExtension;

@UsesOrderedConfiguration(StackExtension.class)
public interface MinificationCacheWarming {

  void warmMinificationCache() throws InterruptedException, ExecutionException;

}
