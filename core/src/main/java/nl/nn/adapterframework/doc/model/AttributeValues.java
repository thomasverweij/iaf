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

package nl.nn.adapterframework.doc.model;

import java.util.List;

import lombok.Getter;

public class AttributeValues {
	private @Getter String fullName;
	private String simpleName;
	private @Getter List<String> values;
	private int seq;

	AttributeValues(String fullName, String simpleName, List<String> values, int seq) {
		this.fullName = fullName;
		this.values = values;
		this.simpleName = simpleName;
		this.seq = seq;
	}

	public String getUniqueName(String groupWord) {
		if(seq == 1) {
			return String.format("%s%s", simpleName, groupWord);
		} else {
			return String.format("%s%s_%d", simpleName, groupWord, seq);
		}
	}
}
