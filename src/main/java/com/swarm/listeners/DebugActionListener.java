package com.swarm.listeners;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaFile;
import com.intellij.xdebugger.impl.actions.*;
import com.swarm.States;
import com.swarm.models.Event;
import com.swarm.models.Method;
import com.swarm.models.Type;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class DebugActionListener implements AnActionListener, DumbAware {

    Project project;
    private long lastEventTime = -1;

    public static Method invokingMethod = new Method();

    public DebugActionListener(Project project) {
        this.project = project;
    }

    private int handleEvent(String eventKind) {
        DebuggerManagerEx debuggerManagerEx = DebuggerManagerEx.getInstanceEx(project);
        var file = (PsiJavaFile) debuggerManagerEx.getContext().getSourcePosition().getFile();

        String typeName = file.getName();
        String typePath = file.getVirtualFile().getPath();
        String sourceCode = file.getText();
        String typeFullName;
        if (!(typeFullName = file.getPackageName()).equals("")) {
            typeFullName += ".";
        }
        typeFullName += typeName;

        int lineNumber = debuggerManagerEx.getContext().getSourcePosition().getLine();

        Type type = new Type();
        type.setSession(States.currentSession);
        type.setFullName(typeFullName);
        type.setName(typeName);
        type.setFullPath(typePath);
        type.setSourceCode(sourceCode);
        type.create();

        Method method = new Method();
        method.setType(type);
        debuggerManagerEx.getContext().getDebugProcess().getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
            @Override
            protected void action() throws Exception {
                try {
                    var frame = DebuggerManagerEx.getInstanceEx(project).getContext().getThreadProxy().frames().get(0);
                    String methodName = frame.location().method().name();
                    String methodSignature = frame.location().method().signature();
                    method.setName(methodName);
                    method.setSignature(methodSignature);
                } catch (EvaluateException e) {
                    e.printStackTrace();
                }
            }
        });
        method.create();

        Event event = new Event();
        event.setSession(States.currentSession);
        event.setMethod(method);
        event.setLineNumber(lineNumber);
        event.setKind(eventKind);
        event.create();

        return method.getId();
    }

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (States.currentSession.getId() == 0) {
            return;
        }
        // The following is a hack to work around an issue with IDEA, where certain events arrive
        // twice. See https://youtrack.jetbrains.com/issue/IDEA-219133
        final InputEvent input = event.getInputEvent();
        if (input instanceof MouseEvent) {
            if (input.getWhen() != 0 && lastEventTime == input.getWhen()) {
                return;
            }
            lastEventTime = input.getWhen();
        }
        if (action instanceof StepIntoAction || action instanceof ForceStepIntoAction) {
            invokingMethod.setId(handleEvent("StepInto"));
            States.isSteppedInto = true;
        } else if (action instanceof StepOverAction || action instanceof ForceStepOverAction) {
            handleEvent("StepOver");
        } else if (action instanceof StepOutAction) {
            handleEvent("StepOut");
        } else if (action instanceof ResumeAction) {
            handleEvent("Resume");
        }
    }
}
