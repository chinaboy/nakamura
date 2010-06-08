/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.site.search;

import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.framework.Constants;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.SearchPropertyProvider;

/**
 *
 * @author chall
 */
@Component
@Properties(value = {
  @Property(name = Constants.SERVICE_VENDOR, value = "The Sakai Foundation"),
  @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "JoinRequest")
})
public class JoinRequestSearchPropertyProvider implements SearchPropertyProvider {

  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
  }
}
