package com.swarm.listeners;

import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.sun.jdi.Method;
import com.swarm.States;
import com.swarm.tools.HTTPUtils;

import java.util.List;


public class DebuggerManagerListenerSwarm implements DebuggerManagerListener, DumbAware {

    Project project;

    public DebuggerManagerListenerSwarm(Project project) {
        this.project = project;
    }

    @Override
    public void sessionCreated(DebuggerSession session) {
        session.getContextManager().addListener((newContext, event) -> {
            if(States.currentSession.getId() == 0) {
                return;
            }
            assert newContext.getDebugProcess() != null;
            newContext.getDebugProcess().getManagerThread().invoke(new DebuggerCommandImpl() {
                @Override
                protected void action(){
                    if (event.name().equals("PAUSE") && States.isSteppedInto) {
                        handleStepInto(newContext);
                    }
                    if (event.name().equals("PAUSE")) {
                        try {
                            assert newContext.getThreadProxy() != null;
                            States.lastStackFrames = newContext.getThreadProxy().frames();
                        } catch (EvaluateException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        });
    }

    private void handleStepInto(DebuggerContextImpl newContext) {
        try {
            States.isSteppedInto = false;
            assert newContext.getThreadProxy() != null;
            List<StackFrameProxyImpl> currentStackFrames = newContext.getThreadProxy().frames();

            if (isInvocation(currentStackFrames)) {
                Method invoked = currentStackFrames.get(0).location().method();
                HTTPUtils.createInvocation(DebugActionListener.invokingMethodId, invoked.name(), invoked.signature(), States.currentSession.getId(), project);
            }
        } catch (EvaluateException e) {
            e.printStackTrace();
        }
    }

    private boolean isInvocation(List<StackFrameProxyImpl> currentStackFrames) {
        if (currentStackFrames.size() < States.lastStackFrames.size()) {
            //here it's certain that it wasn't an invocation because the current stackFrames count is less than before. We just return true
            return false;
        } else
            return currentStackFrames.size() != States.lastStackFrames.size(); //Here if it's not the same frame count, it's an invocation
    }
}
