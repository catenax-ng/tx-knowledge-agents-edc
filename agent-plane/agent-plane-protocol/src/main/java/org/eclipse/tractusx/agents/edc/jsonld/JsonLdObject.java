// Copyright (c) 2022,2023 Contributors to the Eclipse Foundation
//
// See the NOTICE file(s) distributed with this work for additional
// information regarding copyright ownership.
//
// This program and the accompanying materials are made available under the
// terms of the Apache License, Version 2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0.
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
// WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
// License for the specific language governing permissions and limitations
// under the License.
//
// SPDX-License-Identifier: Apache-2.0
package org.eclipse.tractusx.agents.edc.jsonld;

import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import java.util.Map;

public class JsonLdObject {

    protected JsonObject object;

    public JsonLdObject(JsonObject object) {
        this.object=object;
    }

    public Map<String, JsonValue> getProperties() {
        return object;
    }

    public String getId() {
        return object.getString("@id");
    }

    public String asString() {
        return JsonLd.asString(object);
    }
}
