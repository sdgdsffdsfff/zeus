package com.ctrip.zeus.logstats.parser.state;

/**
 * Created by zhoumy on 2016/6/7.
 */
public interface LogStatsStateMachine {

    LogStatsState getStartState();

    void transduce(StateMachineContext ctxt);
}
