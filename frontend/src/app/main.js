import '../core/core.module.js';
import '../core/events/app-events.constant.js';
import '../core/events/chat-events.constant.js';
import '../core/events/message-events.constant.js';
import '../core/events/user-events.constant.js';
import '../core/events/member-events.constant.js';
import '../core/events/event-bus.service.js';
import '../core/http/api-client.service.js';
import '../core/http/http-interceptor.js';
import '../core/socket/socket.service.js';
import '../features/chats/chats.module.js';
import '../features/chats/chats.service.js';
import '../features/chats/conversation-actions.service.js';
import '../features/chats/conversation-activities.service.js';
import '../features/chats/conversation-read.service.js';
import '../features/messages/messages.module.js';
import '../features/messages/messages.service.js';
import '../features/messages/message-actions.service.js';
import '../features/messages/message-history.service.js';
import '../features/messages/message-sending.service.js';
import '../features/messages/message-read-context.service.js';
import '../features/users/users.module.js';
import '../features/users/users.service.js';
import '../features/users/directives/user-directory.directive.js';
import '../features/members/members.module.js';
import '../features/members/members.service.js';
import '../features/channels/channels.module.js';
import '../features/channels/channels.service.js';
import '../features/members/dev-member-context.service.js';
import '../features/chats/chat-state.service.js';
import '../features/chats/chats.controller.js';
import '../features/chats/conversation-actions.controller.js';
import '../features/messages/messages.controller.js';
import '../features/chats/directives/chat-layout.directive.js';
import '../features/chats/directives/chat-sidebar.directive.js';
import '../features/chats/directives/chat-list.directive.js';
import '../features/chats/directives/chat-list-item.directive.js';
import '../features/chats/directives/chat-header.directive.js';
import '../features/chats/directives/conversation-details.directive.js';
import '../features/chats/directives/conversation-history.directive.js';
import '../features/members/directives/member-picker.directive.js';
import '../shared/directives/app-modal.directive.js';
import '../features/messages/directives/message-list.directive.js';
import '../features/messages/directives/message-bubble.directive.js';
import '../features/messages/directives/message-composer.directive.js';
import '../shared/directives/loading-state.directive.js';
import '../shared/directives/empty-state.directive.js';
import '../features/settings/management.service.js';
import '../features/settings/management.directive.js';
import '../features/auth/session.service.js';
import '../features/auth/login.directive.js';
import '../features/auth/bootstrap.service.js';
import '../features/auth/whatsapp-setup.directive.js';
import '../features/auth/whatsapp-setup.css';
import '../shared/directives/cpf-input.directive.js';
import './app.module.js';
import './app.routes.js';
import '../shared/styles/main.css';
import '../shared/styles/directory.css';

import '../features/kanban/kanban.service.js';
import '../features/kanban/kanban.directive.js';
import '../features/kanban/kanban.css';

import '../features/collaboration/team-panel.directive.js';
import '../features/collaboration/team-panel.css';

import '../features/quick-replies/quick-replies.directive.js';
import '../features/dashboard/dashboard.directive.js';

import '../features/sla/sla.directive.js';
import '../features/reports/reports.directive.js';

import '../features/tasks/tasks.directive.js';
import '../features/notifications/notifications.js';

import '../features/collaboration/draft.service.js';

import '../shared/components/directory.directive.js';
import '../features/departments/department-directory.directive.js';

import '../features/settings/settings-hub.directive.js';
import '../shared/components/focus-region.directive.js';
import '../shared/styles/product.css';

import '../features/tasks/tasks.service.js';
import '../features/quick-replies/quick-replies.service.js';
import '../features/sla/sla.service.js';
import '../features/reports/reports.service.js';

import '../features/settings/navigation.directive.js';

import '../shared/components/error-state.directive.js';

import '../shared/styles/workspace-polish.css';

import '../features/channels/channels-page.directive.js';
import '../features/channels/channels.css';
