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
package com.intellij.notification;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author spleaner
 */
public interface Notifications {
  Topic<Notifications> TOPIC = Topic.create("Notifications", Notifications.class);

  String SYSTEM_MESSAGES_GROUP_ID = "System Messages";

  void notify(@NotNull Notification notification);
  void notify(@NotNull Notification notification, @NotNull NotificationDisplayType defaultDisplayType);

  @SuppressWarnings({"UtilityClassWithoutPrivateConstructor"})
  class Bus {
    public static void notify(@NotNull final Notification notification) {
      notify(notification, NotificationDisplayType.BALLOON, null);
    }

    public static void notify(@NotNull final Notification notification, @Nullable Project project) {
      notify(notification, NotificationDisplayType.BALLOON, project);
    }

    public static void notify(@NotNull final Notification notification, @NotNull final NotificationDisplayType defaultDisplayType, @Nullable Project project) {
      final MessageBus bus = project != null ? project.getMessageBus() : ApplicationManager.getApplication().getMessageBus();
      bus.syncPublisher(TOPIC).notify(notification, defaultDisplayType);

    }
  }
}
