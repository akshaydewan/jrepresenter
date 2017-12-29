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

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.TypeName;

public class PropertyAnnotation extends BaseAnnotation {
    public PropertyAnnotation(Attribute modelAttribute, Attribute jsonAttribute,
                              TypeName serializerClassName, TypeName deserializerClassName, TypeName representerClassName,
                              TypeName getterClassName, TypeName setterClassName, TypeName skipParse, TypeName skipRender) {
        super(modelAttribute, jsonAttribute, representerClassName, serializerClassName, deserializerClassName,
                getterClassName, setterClassName, skipParse, skipRender);
    }


    @Override
    protected CodeBlock applySerializer(CodeBlock valueFromGetter) {
        if (hasSerializer()) {
            return CodeBlock.builder()
                    .add("$T.apply(", MapperJavaConstantsFile.SERIALIZE_BUILDER.fieldName(serializerClassName))
                    .add(valueFromGetter)
                    .add(")")
                    .build();
        } else {
            return valueFromGetter;
        }
    }

    @Override
    protected CodeBlock applyDeserializer(CodeBlock valueFromJson) {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add(valueFromJson);

        if (hasDeserializer()) {
            builder.addStatement(
                    "$T $N = $T.apply(($T) jsonAttribute)",
                    modelAttribute.type,
                    MapperJavaSourceFile.DESERIALIZED_JSON_ATTRIBUTE_NAME,
                    MapperJavaConstantsFile.DESERIALIZER_BUILDER.fieldName(deserializerClassName),
                    jsonAttribute.type);
        } else {
            builder.addStatement(
                    "$T $N = ($T) jsonAttribute",
                    modelAttribute.type,
                    MapperJavaSourceFile.DESERIALIZED_JSON_ATTRIBUTE_NAME,
                    jsonAttribute.type);
        }

        return builder.build();
    }
}
