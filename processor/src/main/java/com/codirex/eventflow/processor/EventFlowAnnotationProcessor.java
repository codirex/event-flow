package com.codirex.eventflow.processor;

import com.codirex.eventflow.SubscriberInfo;
import com.codirex.eventflow.ThreadMode;
import com.codirex.eventflow.annotation.Subscribe;
import com.codirex.eventflow.api.EventFlowIndex;
import com.codirex.eventflow.spi.SubscriberInfoProvider;
import com.codirex.eventflow.spi.SubscriberMethodExecutor;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.lang.reflect.InvocationTargetException;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.codirex.eventflow.annotation.Subscribe")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EventFlowAnnotationProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Filer filer;
    private Messager messager;

    private final Map<TypeElement, List<SubscriberInfo>> subscribersMap = new HashMap<>();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
        note(null, "EventBusAnnotationProcessor initialized.");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        note(
                null,
                "EventBusAnnotationProcessor processing round. Annotations: %s, ProcessingOver: %s",
                annotations,
                roundEnv.processingOver());

        for (Element element : roundEnv.getElementsAnnotatedWith(Subscribe.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(
                        element,
                        "Only methods can be annotated with @%s",
                        Subscribe.class.getSimpleName());
                continue;
            }
            ExecutableElement methodElement = (ExecutableElement) element;

            note(
                    methodElement,
                    "Processing method: %s in class %s",
                    methodElement.getSimpleName(),
                    methodElement.getEnclosingElement().getSimpleName());

            if (!isValidSubscriberMethod(methodElement)) {
                continue;
            }

            TypeElement subscriberClassElement = (TypeElement) methodElement.getEnclosingElement();
            Subscribe subscribeAnnotation = methodElement.getAnnotation(Subscribe.class);

            TypeMirror eventTypeMirror = methodElement.getParameters().get(0).asType();

            String eventTypeName = typeUtils.erasure(eventTypeMirror).toString();

            SubscriberInfo subscriberInfo =
                    new SubscriberInfo(
                            subscriberClassElement.getQualifiedName().toString(),
                            methodElement.getSimpleName().toString(),
                            eventTypeName,
                            subscribeAnnotation.threadMode(),
                            subscribeAnnotation.priority(),
                            subscribeAnnotation.sticky());

            subscribersMap
                    .computeIfAbsent(subscriberClassElement, k -> new ArrayList<>())
                    .add(subscriberInfo);
            note(
                    methodElement,
                    "Found and stored valid subscriber method: %s in class %s for event %s",
                    methodElement.getSimpleName(),
                    subscriberClassElement.getQualifiedName(),
                    eventTypeName);
        }

        if (roundEnv.processingOver()) {
            if (!subscribersMap.isEmpty()) {
                try {
                    generateIndexFile();
                } catch (IOException e) {
                    error(null, "Failed to generate EventBusIndex: " + e.getMessage());
                }

                for (Map.Entry<TypeElement, List<SubscriberInfo>> entry :
                        subscribersMap.entrySet()) {
                    TypeElement subscriberClassElement = entry.getKey();
                    List<SubscriberInfo> infos = entry.getValue();

                    for (SubscriberInfo info : infos) {
                        try {
                            generateAdapterClass(subscriberClassElement, info);
                        } catch (IOException e) {
                            error(
                                    subscriberClassElement,
                                    "Could not generate adapter for %s#%s: %s",
                                    subscriberClassElement.getQualifiedName(),
                                    info.getMethodName(),
                                    e.getMessage());
                        }
                    }
                }
            } else {
                note(null, "No subscriber methods found to generate an index or adapters.");
            }
        }
        return true;
    }

    private String generateAdapterName(TypeElement subscriberClassElement, SubscriberInfo info) {
        String subName =
                subscriberClassElement
                        .getQualifiedName()
                        .toString()
                        .replace(".", "_")
                        .replace("$", "__");
        String eventName = info.getEventType().replace(".", "_").replace("$", "__");
        String methodName = info.getMethodName();

        return "_" + subName + "_$$_" + methodName + "_$$_" + eventName + "_$$_Adapter";
    }

    private ExecutableElement findMethodElement(
            TypeElement classElement, String methodName, String eventTypeFqn) {
        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD
                    && enclosedElement.getSimpleName().toString().equals(methodName)) {
                ExecutableElement executable = (ExecutableElement) enclosedElement;
                if (executable.getParameters().size() == 1) {

                    TypeMirror paramType =
                            typeUtils.erasure(executable.getParameters().get(0).asType());
                    if (paramType.toString().equals(eventTypeFqn)) {
                        return executable;
                    }
                }
            }
        }
        return null;
    }

    private void generateAdapterClass(TypeElement subscriberClassElement, SubscriberInfo info)
            throws IOException {
        String adapterPackage = "com.example.eventbus.generated.adapters";
        String adapterName = generateAdapterName(subscriberClassElement, info);

        ClassName subscriberClassName = ClassName.get(subscriberClassElement);
        ClassName eventClassName = ClassName.bestGuess(info.getEventType());

        MethodSpec.Builder invokeMethodBuilder =
                MethodSpec.methodBuilder("invoke")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(Object.class, "target")
                        .addParameter(Object.class, "eventArg")
                        .addException(InvocationTargetException.class)
                        .addStatement(
                                "$T subscriberTyped = ($T) target",
                                subscriberClassName,
                                subscriberClassName)
                        .addStatement(
                                "$T eventTyped = ($T) eventArg", eventClassName, eventClassName);

        ExecutableElement exactMethodElement =
                findMethodElement(
                        subscriberClassElement, info.getMethodName(), info.getEventType());
        if (exactMethodElement == null) {
            error(
                    subscriberClassElement,
                    "Adapter generation: Cannot find method %s for event %s in %s",
                    info.getMethodName(),
                    info.getEventType(),
                    subscriberClassElement.getQualifiedName());
            return;
        }

        List<? extends TypeMirror> thrownTypes = exactMethodElement.getThrownTypes();
        boolean wrapsCheckedExceptions = false;
        if (!thrownTypes.isEmpty()) {
            TypeMirror runtimeExceptionType =
                    elementUtils.getTypeElement("java.lang.RuntimeException").asType();
            TypeMirror errorType = elementUtils.getTypeElement("java.lang.Error").asType();
            for (TypeMirror thrownType : thrownTypes) {
                if (!(typeUtils.isSubtype(thrownType, runtimeExceptionType)
                        || typeUtils.isSubtype(thrownType, errorType))) {
                    wrapsCheckedExceptions = true;
                    break;
                }
            }
        }

        if (wrapsCheckedExceptions) {
            invokeMethodBuilder
                    .beginControlFlow("try")
                    .addStatement("subscriberTyped.$N(eventTyped)", info.getMethodName())
                    .nextControlFlow("catch ($T e)", Exception.class)
                    .addStatement("if (e instanceof RuntimeException) throw (RuntimeException) e")
                    .addStatement("if (e instanceof Error) throw (Error) e")
                    .addStatement("throw new $T(e)", InvocationTargetException.class)
                    .endControlFlow();
        } else {
            invokeMethodBuilder.addStatement(
                    "subscriberTyped.$N(eventTyped)", info.getMethodName());
        }

        MethodSpec invokeMethod = invokeMethodBuilder.build();

        TypeSpec adapterClass =
                TypeSpec.classBuilder(adapterName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addSuperinterface(ClassName.get(SubscriberMethodExecutor.class))
                        .addMethod(invokeMethod)
                        .addJavadoc(
                                "Generated adapter for {@link $T#$N($T)}.",
                                subscriberClassName,
                                info.getMethodName(),
                                eventClassName)
                        .build();

        JavaFile javaFile =
                JavaFile.builder(adapterPackage, adapterClass)
                        .addFileComment("Generated by EventFlowAnnotationProcessor. Do not edit.")
                        .build();
        javaFile.writeTo(filer);
        note(subscriberClassElement, "Generated adapter: %s.%s", adapterPackage, adapterName);
    }

    private void generateIndexFile() throws IOException {
        if (subscribersMap.isEmpty()) {
            note(null, "No subscribers found, not generating EventBusIndex.");
            return;
        }

        String packageName = "com.example.eventbus.generated";
        String className = "MyEventBusIndex";

        MethodSpec.Builder getProvidersMethodBuilder =
                MethodSpec.methodBuilder("getSubscriberInfoProviders")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(
                                ParameterizedTypeName.get(
                                        ClassName.get(List.class),
                                        ClassName.get(SubscriberInfoProvider.class)))
                        .addParameter(ClassName.get(Class.class), "subscriberClass")
                        .addStatement(
                                "$T<$T> providers = new $T<>()",
                                List.class,
                                SubscriberInfoProvider.class,
                                ArrayList.class);

        for (Map.Entry<TypeElement, List<SubscriberInfo>> entry : subscribersMap.entrySet()) {
            TypeElement subscriberTypeElement = entry.getKey();
            List<SubscriberInfo> infos = entry.getValue();

            getProvidersMethodBuilder.beginControlFlow(
                    "if (subscriberClass.getName().equals($S))",
                    subscriberTypeElement.getQualifiedName().toString());

            for (SubscriberInfo info : infos) {
                String adapterPackage = "com.example.eventbus.generated.adapters";
                String adapterName = generateAdapterName(subscriberTypeElement, info);
                ClassName adapterClassName = ClassName.get(adapterPackage, adapterName);

                TypeName eventTypeName = ClassName.bestGuess(info.getEventType());
                TypeName subscriberTypeName = ClassName.get(subscriberTypeElement);

                TypeSpec providerAnonymousClass =
                        TypeSpec.anonymousClassBuilder("")
                                .addSuperinterface(ClassName.get(SubscriberInfoProvider.class))
                                .addMethod(
                                        MethodSpec.methodBuilder("getSubscriberClass")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(Class.class)
                                                .addStatement("return $T.class", subscriberTypeName)
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("getEventType")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(Class.class)
                                                .addStatement("return $T.class", eventTypeName)
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("getThreadMode")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(ClassName.get(ThreadMode.class))
                                                .addStatement(
                                                        "return $T.$L",
                                                        ClassName.get(ThreadMode.class),
                                                        info.getThreadMode().name())
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("getPriority")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(int.class)
                                                .addStatement("return $L", info.getPriority())
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("isSticky")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(boolean.class)
                                                .addStatement("return $L", info.isSticky())
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("getMethodExecutor")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(
                                                        ClassName.get(
                                                                SubscriberMethodExecutor.class))
                                                .addStatement("return new $T()", adapterClassName)
                                                .build())
                                .addMethod(
                                        MethodSpec.methodBuilder("getSubscriberMethodName")
                                                .addAnnotation(Override.class)
                                                .addModifiers(Modifier.PUBLIC)
                                                .returns(String.class)
                                                .addStatement("return $S", info.getMethodName())
                                                .build())
                                .build();
                getProvidersMethodBuilder.addStatement("providers.add($L)", providerAnonymousClass);
            }
            getProvidersMethodBuilder.addStatement("return providers");
            getProvidersMethodBuilder.endControlFlow();
        }

        getProvidersMethodBuilder.addStatement("return $T.emptyList()", Collections.class);
        MethodSpec getProvidersMethod = getProvidersMethodBuilder.build();

        TypeSpec indexClass =
                TypeSpec.classBuilder(className)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addSuperinterface(ClassName.get(EventFlowIndex.class))
                        .addMethod(getProvidersMethod)
                        .addJavadoc("Generated EventBusIndex. Do not edit.")
                        .build();

        JavaFile javaFile =
                JavaFile.builder(packageName, indexClass)
                        .addFileComment("Generated by EventFlowAnnotationProcessor. Do not edit.")
                        .build();
        javaFile.writeTo(filer);
        note(
                null,
                "Generated EventBusIndex: %s.%s with SubscriberInfoProviders",
                packageName,
                className);
    }

    private boolean isValidSubscriberMethod(ExecutableElement methodElement) {
        Element enclosingElement = methodElement.getEnclosingElement();
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            error(
                    methodElement,
                    "Subscriber method %s must be in a class, not %s",
                    methodElement.getSimpleName(),
                    enclosingElement.getKind());
            return false;
        }

        Set<Modifier> modifiers = methodElement.getModifiers();
        if (!modifiers.contains(Modifier.PUBLIC)) {
            error(
                    methodElement,
                    "Subscriber method %s in %s must be public",
                    methodElement.getSimpleName(),
                    enclosingElement.getSimpleName());
            return false;
        }
        if (modifiers.contains(Modifier.STATIC)) {
            error(
                    methodElement,
                    "Subscriber method %s in %s must not be static",
                    methodElement.getSimpleName(),
                    enclosingElement.getSimpleName());
            return false;
        }
        if (modifiers.contains(Modifier.ABSTRACT)) {
            error(
                    methodElement,
                    "Subscriber method %s in %s must not be abstract",
                    methodElement.getSimpleName(),
                    enclosingElement.getSimpleName());
            return false;
        }

        if (methodElement.getParameters().size() != 1) {
            error(
                    methodElement,
                    "Subscriber method %s in %s must have exactly one parameter (the event type)",
                    methodElement.getSimpleName(),
                    enclosingElement.getSimpleName());
            return false;
        }

        TypeElement classElement = (TypeElement) enclosingElement;
        if (!classElement.getModifiers().contains(Modifier.PUBLIC)) {
            error(
                    classElement,
                    "Subscribing class %s must be public",
                    classElement.getQualifiedName());
        }

        return true;
    }

    private void error(Element e, String msg, Object... args) {
        messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
    }

    private void note(Element e, String msg, Object... args) {

        messager.printMessage(Diagnostic.Kind.NOTE, String.format(msg, args), e);
    }
}
