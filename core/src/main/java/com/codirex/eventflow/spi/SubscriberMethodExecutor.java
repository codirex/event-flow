package com.codirex.eventflow.spi;

import java.lang.reflect.InvocationTargetException;

public interface SubscriberMethodExecutor {
	
    void invoke(Object subscriberTarget, Object event) throws InvocationTargetException;

}
