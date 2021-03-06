/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ro.nextreports.server.web.common.behavior;

import org.apache.wicket.Component;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.model.IModel;

/**
 * @author Decebal Suiu
 */
public class ConfirmBehavior extends Behavior {
	
	private static final long serialVersionUID = 1L;
	
	private final IModel<String> message;

     public ConfirmBehavior(IModel<String> message) {
         super();
         
         this.message = message;
     }

     @Override
     public void onComponentTag(Component component, ComponentTag tag) {
         StringBuilder handler = new StringBuilder(128);
         handler.append("if (!confirm('");
         handler.append(message.getObject());
         handler.append("')) {return false;} ");

         String script = tag.getAttributes().getString("onclick");
         if (script != null) {
             handler.append(script);
         }

         tag.put("onclick", handler.toString());
     }

     @Override
     public void detach(Component component) {
         super.detach(component);
         
         message.detach();
     }

}
