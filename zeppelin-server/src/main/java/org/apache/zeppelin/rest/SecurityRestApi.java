/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.rest;


import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.jdbc.JdbcRealm;
import org.apache.shiro.realm.ldap.JndiLdapRealm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.util.ThreadContext;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.zeppelin.annotation.ZeppelinApi;
import org.apache.zeppelin.conf.ZeppelinConfiguration;
import org.apache.zeppelin.server.JsonResponse;
import org.apache.zeppelin.ticket.TicketContainer;
import org.apache.zeppelin.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Zeppelin security rest api endpoint.
 *
 */
@Path("/security")
@Produces("application/json")
public class SecurityRestApi {
  private static final Logger LOG = LoggerFactory.getLogger(SecurityRestApi.class);

  /**
   * Required by Swagger.
   */
  public SecurityRestApi() {
    super();
  }

  /**
   * Get ticket
   * Returns username & ticket
   * for anonymous access, username is always anonymous.
   * After getting this ticket, access through websockets become safe
   *
   * @return 200 response
   */
  @GET
  @Path("ticket")
  @ZeppelinApi
  public Response ticket() {
    ZeppelinConfiguration conf = ZeppelinConfiguration.create();
    String principal = SecurityUtils.getPrincipal();
    HashSet<String> roles = SecurityUtils.getRoles();
    JsonResponse response;
    // ticket set to anonymous for anonymous user. Simplify testing.
    String ticket;
    if ("anonymous".equals(principal))
      ticket = "anonymous";
    else
      ticket = TicketContainer.instance.getTicket(principal);

    Map<String, String> data = new HashMap<>();
    data.put("principal", principal);
    data.put("roles", roles.toString());
    data.put("ticket", ticket);

    response = new JsonResponse(Response.Status.OK, "", data);
    LOG.warn(response.toString());
    return response.build();
  }

  /**
   * Get userlist
   * Returns list of all user from available realms
   *
   * @return 200 response
   */
  @GET
  @Path("userlist/{searchText}")
  public Response getUserList(@PathParam("searchText") String searchText) {

    List<String> usersList = new ArrayList<>();
    try {
      GetUserList getUserListObj = new GetUserList();
      DefaultWebSecurityManager defaultWebSecurityManager;
      String key = ThreadContext.SECURITY_MANAGER_KEY;
      defaultWebSecurityManager = (DefaultWebSecurityManager) ThreadContext.get(key);
      Collection<Realm> realms = defaultWebSecurityManager.getRealms();
      List realmsList = new ArrayList(realms);
      for (int i = 0; i < realmsList.size(); i++) {
        String name = ((Realm) realmsList.get(i)).getName();
        if (name.equals("iniRealm")) {
          usersList.addAll(getUserListObj.getUserList((IniRealm) realmsList.get(i)));
        } else if (name.equals("ldapRealm")) {
          usersList.addAll(getUserListObj.getUserList((JndiLdapRealm) realmsList.get(i)));
        } else if (name.equals("jdbcRealm")) {
          usersList.addAll(getUserListObj.getUserList((JdbcRealm) realmsList.get(i)));
        }
      }

    } catch (Exception e) {
      LOG.error("Exception in retrieving Users from realms ", e);
    }
    List<String> autoSuggestList = new ArrayList<>();
    Collections.sort(usersList);
    int maxLength = 0;
    for (int i = 0; i < usersList.size(); i++) {
      String userLowerCase = usersList.get(i).toLowerCase();
      String searchTextLowerCase = searchText.toLowerCase();
      if (userLowerCase.indexOf(searchTextLowerCase) != -1) {
        maxLength++;
        autoSuggestList.add(usersList.get(i));
      }
      if (maxLength == 5) {
        break;
      }
    }
    return new JsonResponse<>(Response.Status.OK, "", autoSuggestList).build();
  }

}
