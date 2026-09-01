package com.polaris.bpm.config;

import com.polaris.bpm.listener.FlowableLifecycleEventListener;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.ProcessEngineConfigurationConfigurer;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Registers engine listeners before the Flowable process engine is built. */
@Configuration
public class FlowableEventConfiguration implements ProcessEngineConfigurationConfigurer {
    private final FlowableLifecycleEventListener lifecycleEventListener;

    public FlowableEventConfiguration(FlowableLifecycleEventListener lifecycleEventListener) {
        this.lifecycleEventListener = lifecycleEventListener;
    }

    @Override
    public void configure(SpringProcessEngineConfiguration configuration) {
        configuration.setEventListeners(List.of(lifecycleEventListener));
    }
}
