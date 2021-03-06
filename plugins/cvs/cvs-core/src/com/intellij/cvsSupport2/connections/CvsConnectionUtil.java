/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ext.ExtConnection;
import com.intellij.cvsSupport2.connections.ssh.*;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerConnection;

import java.io.File;

public class CvsConnectionUtil {
  public static final int DEFAULT_PSERVER_PORT = 2401;

  private CvsConnectionUtil() {
  }

  public static IConnection createSshConnection(final CvsRootData settings,
                                                final SshSettings sshConfiguration,
                                                final ProxySettings proxySettings,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final int timeout) {
    ConnectionSettingsImpl connectionSettings = new ConnectionSettingsImpl(settings.HOST,
                                                                           settings.PORT,
                                                                           proxySettings.USE_PROXY,
                                                                           proxySettings.PROXY_HOST,
                                                                           proxySettings.PROXY_PORT,
                                                                           timeout,
                                                                           proxySettings.getType(),
                                                                           proxySettings.getLogin(),
                                                                           proxySettings.getPassword());
    //final EmptyPool pool = EmptyPool.getInstance();
    final ConnectionPoolI pool = SshConnectionPool.getInstance();
    final SshAuthentication authentication;
    if (sshConfiguration.USE_PPK) {
      authentication = new SshPublicKeyAuthentication(new File(sshConfiguration.PATH_TO_PPK),
            sshPasswordProvider.getPPKPasswordForCvsRoot(settings.getCvsRootAsString()), settings.USER);
    }
    else {
      authentication = new SshPasswordAuthentication(settings.USER, sshPasswordProvider.getPasswordForCvsRoot(settings.getCvsRootAsString()));
    }
    return pool.getConnection(settings.REPOSITORY, connectionSettings, authentication);
  }

  public static int getPort(final String port) {
    if (port.length() == 0) return 22;
    try {
      return Integer.parseInt(port);
    }
    catch (NumberFormatException e) {
      return 22;
    }
  }

  public static IConnection createExtConnection(final CvsRootData settings,
                                                final ExtConfiguration extConfiguration,
                                                final SshSettings sshConfiguration,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final ProxySettings proxySettings,
                                                final ErrorRegistry errorRegistry,
                                                final int timeout) {
    if (extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION) {
      return createSshConnection(settings, sshConfiguration, proxySettings,
                                 sshPasswordProvider,
                                 timeout);
    }
    else {
      return new ExtConnection(settings.HOST, settings.USER, settings.REPOSITORY, extConfiguration, errorRegistry);
    }
  }

  public static IConnection createPServerConnection(CvsRootData root, ProxySettings proxySettings, final int timeout) {
    ConnectionSettings connectionSettings = new ConnectionSettingsImpl(root.HOST,
                                                                       root.PORT,
                                                                       proxySettings.USE_PROXY,
                                                                       proxySettings.PROXY_HOST,
                                                                       proxySettings.PROXY_PORT,
                                                                       timeout,
                                                                       proxySettings.TYPE,
                                                                       proxySettings.getLogin(),
                                                                       proxySettings.getPassword());

    return new PServerConnection(connectionSettings, root.USER, root.PASSWORD, adjustRepository(root));
  }

  public static String adjustRepository(CvsRootData root) {
    if (root.REPOSITORY != null) {
      return root.REPOSITORY.replace('\\', '/');
    } else {
      return null;
    }
  }
}
