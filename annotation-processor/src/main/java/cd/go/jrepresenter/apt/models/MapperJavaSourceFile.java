/*
 * Copyright 2017 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cd.go.jrepresenter.apt.models;

import cd.go.jrepresenter.LinksMapper;
import cd.go.jrepresenter.LinksProvider;
import cd.go.jrepresenter.RequestContext;
import cd.go.jrepresenter.apt.util.TypeUtil;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MapperJavaSourceFile {
    private static final ParameterizedTypeName MAP_OF_STRING_TO_OBJECT = TypeUtil.mapOf(Map.class, ClassName.get(String.class), ClassName.get(Object.class));
    private static final ParameterizedTypeName LINKED_HASH_MAP_OF_STRING_TO_OBJECT = TypeUtil.mapOf(LinkedHashMap.class, ClassName.get(String.class), ClassName.get(Object.class));

    public static final String JSON_ATTRIBUTE_VARIABLE_NAME = "jsonAttribute";
    public static final String MODEL_ATTRIBUTE_VARIABLE_NAME = "modelAttribute";
    public static final String JSON_OBJECT_VAR_NAME = "jsonObject";
    public static final String EMBEDDED_MAP_VARIABLE_NAME = "embeddedMap";
    public static final String DESERIALIZED_JSON_ATTRIBUTE_NAME = "deserializedJsonAttribute";
    public static final String LINKS_PROVIDER_CONST_NAME = "LINKS_PROVIDER";
    public static final String JSON_ARRAY_VAR_NAME = "jsonArray";

    public final RepresenterAnnotation representerAnnotation;
    private final ClassToAnnotationMap context;

    public MapperJavaSourceFile(RepresenterAnnotation representerAnnotation, ClassToAnnotationMap context) {
        this.representerAnnotation = representerAnnotation;
        this.context = context;
    }

    public String toSource() {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(representerAnnotation.mapperClassImplSimpleName())
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Representer for {@link $T}.\n", representerAnnotation.getModelClass())
                .addJavadoc("Generated using representer {@link $T}.\n", representerAnnotation.getRepresenterClass());

        if (!representerAnnotation.shouldSkipSerialize()) {
            classBuilder
                    .addMethod(toJsonMethod())
                    .addMethod(toJsonCollectionMethod());
        }

        if (!representerAnnotation.shouldSkipDeserialize()) {
            classBuilder
                    .addMethod(fromJsonMethod())
                    .addMethod(fromJsonCollectionMethod());
        }

        if (representerAnnotation.hasLinksProvider()) {
            classBuilder.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(LinksProvider.class), representerAnnotation.getModelClass()), LINKS_PROVIDER_CONST_NAME, Modifier.STATIC, Modifier.PRIVATE)
                    .initializer(CodeBlock.builder().add("new $T()", representerAnnotation.getLinksProviderClass()).build())
                    .build());
        }
        return JavaFile.builder(representerAnnotation.packageNameRelocated(), classBuilder.build())
                .addFileComment("\n")
                .addFileComment("This file was automatically generated by jrepresenter\n")
                .addFileComment("Any changes may be lost!\n")
                .build().toString();
    }

    private MethodSpec toJsonCollectionMethod() {
        return MethodSpec.methodBuilder("toJSON")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(TypeUtil.listOf(representerAnnotation.getModelClass()), "values")
                .addParameter(RequestContext.class, "requestContext")
                .returns(List.class)
                .addCode(
                        CodeBlock.builder()
                                .addStatement("return values.stream().map(eachItem -> $T.toJSON(eachItem, requestContext)).collect($T.toList())", representerAnnotation.mapperClassImplRelocated(), Collectors.class)
                                .build()
                )
                .build();

    }

    private MethodSpec fromJsonCollectionMethod() {
        ParameterizedTypeName listOfMaps = TypeUtil.listOf(Map.class);
        ParameterizedTypeName listOfModels = TypeUtil.listOf(representerAnnotation.getModelClass());
        return MethodSpec.methodBuilder("fromJSON")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(listOfMaps, JSON_ARRAY_VAR_NAME)
                .returns(listOfModels)
                .addCode(
                        CodeBlock.builder()
                                .beginControlFlow("if ($N == null)", JSON_ARRAY_VAR_NAME)
                                .addStatement("return $T.emptyList()", Collections.class)
                                .endControlFlow()
                                .addStatement("return $N.stream().map(eachItem -> $T.fromJSON(eachItem)).collect($T.toList())", JSON_ARRAY_VAR_NAME, representerAnnotation.mapperClassImplRelocated(), Collectors.class)
                                .build()
                )
                .build();

    }

    private MethodSpec toJsonMethod() {
        return MethodSpec.methodBuilder("toJSON")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(representerAnnotation.getModelClass(), "value")
                .addParameter(RequestContext.class, "requestContext")
                .returns(MAP_OF_STRING_TO_OBJECT)
                .addCode(
                        CodeBlock.builder()
                                .addStatement("$T $N = new $T()", MAP_OF_STRING_TO_OBJECT, JSON_OBJECT_VAR_NAME, LINKED_HASH_MAP_OF_STRING_TO_OBJECT)
                                .add(serializeInternal())
                                .add(serializeForSubClasses())
                                .addStatement("return $N", JSON_OBJECT_VAR_NAME)
                                .build()
                )
                .build();

    }

    private MethodSpec fromJsonMethod() {
        CodeBlock methodBody;
        if (representerAnnotation.hasDeserializerClass()) {
            methodBody = CodeBlock.builder()
                    .addStatement("return $T.apply($N)", MapperJavaConstantsFile.CUSTOM_REPRESENTER_BUILDER.fieldName(representerAnnotation.getDeserializerClass()), JSON_OBJECT_VAR_NAME)
                    .build();
        } else {
            methodBody = CodeBlock.builder()
                    .add(createNewModelObject())
                    .add(maybeReturnEarly(JSON_OBJECT_VAR_NAME, "model"))
                    .add(deserializeInternal())
                    .addStatement("return model")
                    .build();
        }

        return MethodSpec.methodBuilder("fromJSON")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(Map.class, JSON_OBJECT_VAR_NAME)
                .returns(representerAnnotation.getModelClass())
                .addCode(methodBody)
                .build();
    }

    private CodeBlock maybeReturnEarly(String jsonObjectVarName, String model) {
        return CodeBlock.builder()
                .beginControlFlow("if ($N == null)", jsonObjectVarName)
                .addStatement("return $N", model)
                .endControlFlow()
                .build();
    }

    private CodeBlock createNewModelObject() {
        return representerAnnotation.getRepresentsSubClassesAnnotation()
                .map(subClassesAnnotation -> subClassesAnnotation.getDeserializeCodeBlock(context, representerAnnotation))
                .orElse(
                        CodeBlock.builder()
                                .addStatement("$T model = new $T()", representerAnnotation.getModelClass(), representerAnnotation.getModelClass())
                                .build());
    }

    private CodeBlock serializeInternal() {
        CodeBlock.Builder serializeInternalBuilder = CodeBlock.builder();

        if (representerAnnotation.hasLinksProvider()) {
            serializeInternalBuilder.addStatement("$N.putAll($T.toJSON($N, $N, $N))", JSON_OBJECT_VAR_NAME, LinksMapper.class, "LINKS_PROVIDER", "value", "requestContext");
        }

        List<BaseAnnotation> nonEmbeddedAnnotations = context.getAnnotationsOn(representerAnnotation).stream().filter(baseAnnotation -> !baseAnnotation.isEmbedded()).collect(Collectors.toList());
        List<BaseAnnotation> embeddedAnnotations = context.getAnnotationsOn(representerAnnotation).stream().filter(BaseAnnotation::isEmbedded).collect(Collectors.toList());

        nonEmbeddedAnnotations.forEach(baseAnnotation -> serializeInternalBuilder.add(baseAnnotation.getSerializeCodeBlock(context, JSON_OBJECT_VAR_NAME)));

        if (!embeddedAnnotations.isEmpty()) {
            serializeInternalBuilder.addStatement("$T $N = new $T()", MAP_OF_STRING_TO_OBJECT, EMBEDDED_MAP_VARIABLE_NAME, LINKED_HASH_MAP_OF_STRING_TO_OBJECT);

            embeddedAnnotations.forEach(baseAnnotation -> serializeInternalBuilder.add(baseAnnotation.getSerializeCodeBlock(context, EMBEDDED_MAP_VARIABLE_NAME)));

            serializeInternalBuilder.addStatement("$N.put($S, $N)", JSON_OBJECT_VAR_NAME, "_embedded", EMBEDDED_MAP_VARIABLE_NAME);
        }

        return serializeInternalBuilder.build();
    }

    private CodeBlock serializeForSubClasses() {
        CodeBlock.Builder builder = CodeBlock.builder();
        representerAnnotation.getRepresentsSubClassesAnnotation().ifPresent(representsSubClassesAnnotation -> builder.add(representsSubClassesAnnotation.getSerializeCodeBlock(context)));
        return builder.build();
    }

    private CodeBlock deserializeInternal() {
        CodeBlock.Builder deserializeInternalBuilder = CodeBlock.builder();
        context.getAnnotationsOn(representerAnnotation).forEach(baseAnnotation -> deserializeInternalBuilder.add(baseAnnotation.getDeserializeCodeBlock(context)));
        return deserializeInternalBuilder.build();
    }

}
