package com.swarm.listeners;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.xdebugger.impl.actions.*;
import com.swarm.toolWindow.ProductToolWindow;
import com.swarm.utils.States;
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

        Type type = new Type();
        ReadAction.run(() -> {
            PsiFile file = debuggerManagerEx.getContext().getSourcePosition().getFile();
            type.setSourceCode(file.getText());
            type.setFullPath(file.getVirtualFile().getPath());
        });

        type.setSession(ProductToolWindow.getCurrentSession());

        debuggerManagerEx.getContext().getDebugProcess().getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
            @Override
            protected void action() {
                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiMethod psiMethod = (PsiMethod) DebuggerUtilsEx.getContainingMethod(debuggerManagerEx.getContext().getSourcePosition());
                    PsiClass psiClass = PsiTreeUtil.getParentOfType(psiMethod, PsiClass.class);

                    PsiClass newClass;
                    while ((newClass = PsiTreeUtil.getParentOfType(psiClass, PsiClass.class)) != null) {
                        psiClass = newClass;
                    }

                    type.setName(psiClass.getName());
                    type.setFullName(psiClass.getQualifiedName());
                });
            }
        });
        type.create();

        Method method = new Method();
        method.setType(type);
        debuggerManagerEx.getContext().getDebugProcess().getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
            @Override
            protected void action() {
                try {
                    var frame = DebuggerManagerEx.getInstanceEx(project).getContext().getThreadProxy().frames().get(0);
                    String methodName = frame.location().method().name();
                    method.setName(methodName);

                    ApplicationManager.getApplication().runReadAction(() -> {
                        PsiMethod psiMethod = (PsiMethod) DebuggerUtilsEx.getContainingMethod(DebuggerManagerEx.getInstanceEx(project).getContext().getSourcePosition());
                        PsiMethod newMethod;
                        while ((newMethod = PsiTreeUtil.getParentOfType(psiMethod, PsiMethod.class)) != null) {
                            psiMethod = newMethod;
                        }
                        String methodReturnType;
                        if (psiMethod.getReturnType() == null) {
                            methodReturnType = "Constructor";
                        } else {
                            methodReturnType = psiMethod.getReturnType().getCanonicalText();
                        }
                        PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
                        String methodSignature = encodeSignature(parameters, methodReturnType);
                        method.setSignature(methodSignature);
                    });
                } catch (EvaluateException e) {
                    e.printStackTrace();
                }
            }
        });
        method.create();

        int lineNumber = debuggerManagerEx.getContext().getSourcePosition().getLine();

        Event event = new Event();
        event.setSession(ProductToolWindow.getCurrentSession());
        event.setMethod(method);
        event.setLineNumber(lineNumber);
        event.setKind(eventKind);
        event.create();

        return method.getId();
    }

    private String encodeSignature(PsiParameter[] parameters, String returnType) {
        String result = "(";
        for (int i = 0; i < parameters.length; i++) {
            result += parameters[i].getType().getCanonicalText();
            if (i != parameters.length - 1) {
                result += ",";
            }
        }
        result += ")" + returnType;
        return result;
    }

    @Override
    public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (ProductToolWindow.getCurrentSessionId() == 0) {
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
