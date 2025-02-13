/* 
Copyright 2021 WeAreFrank! 

Licensed under the Apache License, Version 2.0 (the "License"); 
you may not use this file except in compliance with the License. 
You may obtain a copy of the License at 

    http://www.apache.org/licenses/LICENSE-2.0 

Unless required by applicable law or agreed to in writing, software 
distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
See the License for the specific language governing permissions and 
limitations under the License. 
*/

package nl.nn.adapterframework.doc.doclet;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class FrankAnnotationReflect implements FrankAnnotation {
	private Annotation annotation;

	FrankAnnotationReflect(Annotation annotation) {
		this.annotation = annotation;
	}

	@Override
	public String getName() {
		return annotation.annotationType().getName();
	}
	
	@Override
	public boolean isPublic() {
		return Modifier.isPublic(annotation.annotationType().getModifiers());
	}

	public FrankAnnotation[] getAnnotations() {
		return new FrankAnnotation[] {};
	}

	public FrankAnnotation getAnnotation(String name) {
		return null;
	}

	@Override
	public Object getValue() throws FrankDocException {
		try {
			Method valueMethod = annotation.annotationType().getMethod("value");
			return valueMethod.invoke(annotation);
		} catch(Exception e) {
			throw new FrankDocException(String.format("Could not get value of annotation [%s]", getName()), e);
		}
	}
}
