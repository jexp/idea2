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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Oct 31, 2002
 * Time: 6:33:01 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.reporter.ConnectionException;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * XML sample:
 * <idea>
 * <build>456</build>
 * <version>4.5.2</version>
 * <title>New Intellij IDEA Version</title>
 * <message>
 * New version of IntelliJ IDEA is available.
 * Please visit http://www.intellij.com/ for more info.
 * </message>
 * </idea>
 */
public final class UpdateChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");

  public static String ADDITIONAL_REQUEST_OPTIONS = "";

  public static enum DownloadPatchResult {
    SUCCESS, FAILED, CANCELED
  }

  private static long checkInterval = 0;
  private static boolean myVeryFirstOpening = true;


  @NonNls
  private static final String DISABLED_UPDATE = "disabled_update.txt";
  private static TreeSet<String> ourDisabledToUpdatePlugins;

  private static class StringHolder {
    private static final String UPDATE_URL = ApplicationInfoEx.getInstanceEx().getUpdateUrls().getCheckingUrl();
    private static final String PATCHES_URL = ApplicationInfoEx.getInstanceEx().getUpdateUrls().getPatchesUrl();
  }

  private static String getUpdateUrl() {
    return StringHolder.UPDATE_URL;
  }

  private static String getPatchesUrl() {
    return StringHolder.PATCHES_URL;
  }

  public static boolean isMyVeryFirstOpening() {
    return myVeryFirstOpening;
  }

  public static void setMyVeryFirstOpening(final boolean myVeryFirstProjectOpening) {
    myVeryFirstOpening = myVeryFirstProjectOpening;
  }

  public static boolean checkNeeded() {

    final UpdateSettings settings = UpdateSettings.getInstance();
    if (settings == null || getUpdateUrl() == null) return false;

    final String checkPeriod = settings.CHECK_PERIOD;
    if (checkPeriod.equals(UpdateSettingsConfigurable.ON_START_UP)) {
      checkInterval = 0;
    }
    if (checkPeriod.equals(UpdateSettingsConfigurable.DAILY)) {
      checkInterval = DateFormatUtil.DAY;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.WEEKLY)) {
      checkInterval = DateFormatUtil.WEEK;
    }
    if (settings.CHECK_PERIOD.equals(UpdateSettingsConfigurable.MONTHLY)) {
      checkInterval = DateFormatUtil.MONTH;
    }

    final long timeDelta = System.currentTimeMillis() - settings.LAST_TIME_CHECKED;
    if (Math.abs(timeDelta) < checkInterval) return false;

    return settings.CHECK_NEEDED;
  }

  public static List<PluginDownloader> updatePlugins(final boolean showErrorDialog) {
    final List<PluginDownloader> downloaded = new ArrayList<PluginDownloader>();
    final Set<String> failed = new HashSet<String>();
    for (String host : UpdateSettingsConfigurable.getInstance().getPluginHosts()) {
      try {
        checkPluginsHost(host, downloaded);
      }
      catch (Exception e) {
        LOG.info(e);
        failed.add(host);
      }
    }
    if (!failed.isEmpty()) {
      final String failedMessage = IdeBundle.message("connection.failed.message", StringUtil.join(failed, ","));
      if (showErrorDialog) {
        Messages.showErrorDialog(failedMessage, IdeBundle.message("title.connection.error"));
      }
      else {
        LOG.info(failedMessage);
      }
    }
    return downloaded.isEmpty() ? null : downloaded;
  }

  public static boolean checkPluginsHost(final String host, final List<PluginDownloader> downloaded) throws Exception {
    final Document document = loadVersionInfo(host);
    if (document == null) return false;
    boolean success = true;
    for (Object plugin : document.getRootElement().getChildren("plugin")) {
      Element pluginElement = (Element)plugin;
      final String pluginId = pluginElement.getAttributeValue("id");
      final String pluginUrl = pluginElement.getAttributeValue("url");
      final String pluginVersion = pluginElement.getAttributeValue("version");
      if (pluginId == null) {
        LOG.info("plugin id should not be null");
        success = false;
        continue;
      }

      if (pluginUrl == null) {
        LOG.info("plugin url should not be null");
        success = false;
        continue;
      }

      ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        public void run() {
          try {
            final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
            if (progressIndicator != null) {
              progressIndicator.setText(pluginUrl);
            }
            final PluginDownloader uploader = new PluginDownloader(pluginId, pluginUrl, pluginVersion);
            if (uploader.prepareToInstall()) {
              downloaded.add(uploader);
            }
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }, IdeBundle.message("update.uploading.plugin.progress.title"), true, null);
    }
    return success;
  }

  @Nullable
  private static Product findProduct(Element products, String code) {
    for (Object productNode : products.getChildren("product")) {
      Product product = new Product((Element)productNode);
      if (product.hasCode(code)) {
        return product;
      }
    }
    return null;
  }

  @Nullable
  public static UpdateChannel checkForUpdates() throws ConnectionException {
    try {
      BuildNumber ourBuild = ApplicationInfo.getInstance().getBuild();
      if (LOG.isDebugEnabled()) {
        LOG.debug("enter: checkForUpdates()");
      }

      final Document document;
      try {
        document = loadVersionInfo(getUpdateUrl());
        if (document == null) return null;
      }
      catch (Throwable t) {
        LOG.debug(t);
        throw new ConnectionException(t);
      }

      Element root = document.getRootElement();
      UpdateChannel channel = findUpdateChannel(root, ourBuild.getProductCode());
      if (channel == null) return null;

      BuildInfo latestBuild = channel.getLatestBuild();
      if (latestBuild == null) return null;

      if (ourBuild.compareTo(latestBuild.getNumber()) < 0) {
        return channel;
      }

      return null;
    }
    catch (Throwable t) {
      LOG.debug(t);
      return null;
    }
    finally {
      UpdateSettings.getInstance().LAST_TIME_CHECKED = System.currentTimeMillis();
    }
  }

  @Nullable
  private static UpdateChannel findUpdateChannel(Element root, String productCode) {
    if (root == null) {
      LOG.info("cannot read " + getUpdateUrl());
      return null;
    }

    Product product = findProduct(root, productCode);
    if (product == null) return null;

    UpdateChannel channel = product.findUpdateChannelById(UpdateSettings.getInstance().CURRENT_UPDATE_CHANNEL);
    if (channel != null) return channel;

    for (UpdateChannel c : product.getChannels()) {
      BuildInfo cBuild = c.getLatestBuild();
      if (cBuild == null) continue;

      if (channel == null) {
        channel = c;
      }
      else {
        BuildInfo build = channel.getLatestBuild();
        assert build != null;

        if (build.compareTo(cBuild) < 0) {
          channel = c;
        }
      }
    }

    return channel;
  }

  private static Document loadVersionInfo(final String url) throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("enter: loadVersionInfo(UPDATE_URL='" + url + "' )");
    }
    final Document[] document = new Document[]{null};
    final Exception[] exception = new Exception[]{null};
    Future<?> downloadThreadFuture = ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          HttpConfigurable.getInstance().prepareURL(url);
          final URL requestUrl = new URL(url + "?build=" + ApplicationInfo.getInstance().getBuild().asString() + ADDITIONAL_REQUEST_OPTIONS);
          final InputStream inputStream = requestUrl.openStream();
          try {
            document[0] = JDOMUtil.loadDocument(inputStream);
          }
          finally {
            inputStream.close();
          }
        }
        catch (IOException e) {
          exception[0] = e;
        }
        catch (JDOMException e) {
          LOG.info(e); // Broken xml downloaded. Don't bother telling user.
        }
      }
    });

    try {
      downloadThreadFuture.get(5, TimeUnit.SECONDS);
    }
    catch (TimeoutException e) {
      // ignore
    }

    if (!downloadThreadFuture.isDone()) {
      downloadThreadFuture.cancel(true);
      throw new ConnectionException(IdeBundle.message("updates.timeout.error"));
    }

    if (exception[0] != null) throw exception[0];
    return document[0];
  }

  public static void showNoUpdatesDialog(boolean enableLink, final List<PluginDownloader> updatePlugins) {
    NoUpdatesDialog dialog = new NoUpdatesDialog(true, updatePlugins, enableLink);
    dialog.show();
  }

  public static void showUpdateInfoDialog(boolean enableLink, final UpdateChannel channel, final List<PluginDownloader> updatePlugins) {
    new UpdateInfoDialog(true, channel, updatePlugins, enableLink).show();
  }

  public static boolean install(List<PluginDownloader> downloaders) {
    boolean installed = false;
    for (PluginDownloader downloader : downloaders) {
      if (getDisabledToUpdatePlugins().contains(downloader.getPluginId())) continue;
      try {
        downloader.install();
        installed = true;
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return installed;
  }


  public static DownloadPatchResult downloadAndInstallPatch(final BuildInfo newVersion) {
    final DownloadPatchResult[] result = new DownloadPatchResult[]{DownloadPatchResult.CANCELED};

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        try {
          doDownloadAndInstallPatch(newVersion, ProgressManager.getInstance().getProgressIndicator());
          result[0] = DownloadPatchResult.SUCCESS;
        }
        catch (final IOException e) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(e.getMessage(), "Failed to download patch file");
            }
          });
          LOG.info(e);
          result[0] = DownloadPatchResult.FAILED;
        }
      }
    }, IdeBundle.message("update.downloading.patch.progress.title"), true, null)) {
      return DownloadPatchResult.CANCELED;
    }

    return result[0];
  }

  private static void doDownloadAndInstallPatch(BuildInfo newVersion, ProgressIndicator i) throws IOException {
    PatchInfo patch = newVersion.findPatchForCurrentBuild();
    if (patch == null) throw new IOException("No patch is available for current version");

    String platform = System.getProperty("idea.platform.prefix", "idea");

    String osSuffix = "";
    if (SystemInfo.isWindows) {
      osSuffix = "-win";
    }
    else if (SystemInfo.isMac) {
      osSuffix = "-mac";
    }
    else if (SystemInfo.isUnix) osSuffix = "-unix";

    String fileName = platform + "-" + patch.getFromBuild().asString() + "-" + newVersion.getNumber().asString() + "-patch" + osSuffix + ".jar";
    URLConnection connection = null;
    InputStream in = null;
    OutputStream out = null;

    String patchFileName = "jetbrains.patch.jar." + platform;
    File patchFile = new File(FileUtil.getTempDirectory(), patchFileName);

    try {
      connection = new URL(new URL(getPatchesUrl()), fileName).openConnection();
      in = UrlConnectionUtil.getConnectionInputStreamWithException(connection, i);
      out = new BufferedOutputStream(new FileOutputStream(patchFile));

      i.setIndeterminate(false);

      byte[] buffer = new byte[10 * 1024];
      int total = connection.getContentLength();
      int count;
      int read = 0;

      while ((count = in.read(buffer)) > 0) {
        i.checkCanceled();
        out.write(buffer, 0, count);
        read += count;
        i.setFraction(((double)read) / total);
        i.setText2((read / 1024) + "/" + (total / 1024) + " KB");
      }
    }
    catch (IOException e) {
      patchFile.delete();
      throw e;
    }
    catch (ProcessCanceledException e) {
      patchFile.delete();
      throw e;
    }
    catch (Throwable e) {
      patchFile.delete();
      throw new RuntimeException(e);
    }
    finally {
      if (out != null) out.close();
      if (in != null) in.close();
      if (connection instanceof HttpURLConnection) ((HttpURLConnection)connection).disconnect();
    }
  }

  public static Set<String> getDisabledToUpdatePlugins() {
    if (ourDisabledToUpdatePlugins == null) {
      ourDisabledToUpdatePlugins = new TreeSet<String>();
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          final File file = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
          if (file.isFile()) {
            final String[] ids = new String(FileUtil.loadFileText(file)).split("[\\s]");
            for (String id : ids) {
              if (id != null && id.trim().length() > 0) {
                ourDisabledToUpdatePlugins.add(id.trim());
              }
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return ourDisabledToUpdatePlugins;
  }

  public static void saveDisabledToUpdatePlugins() {
    try {
      File plugins = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
      FileUtil.ensureCanCreateFile(plugins);

      PrintWriter printWriter = null;
      try {
        printWriter = new PrintWriter(new BufferedWriter(new FileWriter(plugins)));
        for (String id : getDisabledToUpdatePlugins()) {
          printWriter.println(id);
        }
        printWriter.flush();
      }
      finally {
        if (printWriter != null) {
          printWriter.close();
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }
}
