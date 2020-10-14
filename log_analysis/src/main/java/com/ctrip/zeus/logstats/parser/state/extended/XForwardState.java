package com.ctrip.zeus.logstats.parser.state.extended;

import com.ctrip.zeus.logstats.parser.state.Action;
import com.ctrip.zeus.logstats.parser.state.ListStringState;
import com.ctrip.zeus.logstats.parser.state.LogStatsState;
import com.ctrip.zeus.logstats.parser.state.LogStatsStateMachine;

/**
 * Created by zhoumy on 2016/6/7.
 */
public class XForwardState implements LogStatsState {
    private final String name;
    private final LogStatsStateMachine subMachine;
    private LogStatsState next;

    public XForwardState(String name,String separator) {
        this.name = name;
        this.subMachine = new XForwardForStateMachine(new ListStringState(name, ", "),separator);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public LogStatsStateMachine getSubMachine() {
        return subMachine;
    }

    @Override
    public Action getAction() {
        return null;
    }

    @Override
    public void setNext(LogStatsState next) {
        this.next = next;
    }

    @Override
    public LogStatsState getNext() {
        return next;
    }

    @Override
    public boolean runSubMachine() {
        return true;
    }
}
