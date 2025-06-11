package com.codirex.eventflow.api;

import com.codirex.eventflow.spi.SubscriberInfoProvider;
import java.util.List;

public interface EventFlowIndex {
    List<SubscriberInfoProvider> getSubscriberInfoProviders(Class<?> subscriberClass);
}
