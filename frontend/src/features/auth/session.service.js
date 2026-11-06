import angular from 'angular';
angular
  .module('wxn.core')
  .factory('AccountSessionState', function () {
    return { user: null, csrfToken: '', ready: false };
  })
  .factory('AccountSessionService', [
    '$injector',
    'ApiClientService',
    'AccountSessionState',
    function ($injector, api, state) {
      let pending;
      function clearPrivateState() {
        const chats = $injector.get('ChatStateService');
        chats.select(null);
        chats.resetQuery();
        chats.state.unread = {};
        chats.state.readerId = null;
        chats.state.responsibleId = null;
        chats.state.search = '';
        $injector.get('SocketService').disconnect();
        const actions = $injector.get('MessageActionsService').state;
        Object.assign(actions, {
          conversationId: null,
          reply: null,
          forwarding: null,
          deleting: null,
          destinations: [],
          capabilities: [],
          writable: false,
        });
        const notifications = $injector.get('NotificationCenterService').state;
        notifications.items = [];
        notifications.count = 0;
        const members = $injector.get('DevMemberContextService').state;
        Object.assign(members, { member: null, members: [], availableHumans: [] });
      }
      function apply(result) {
        if (state.user?.id !== result.user?.id || state.user?.role !== result.user?.role) {
          clearPrivateState();
        }
        state.user = result.user;
        state.csrfToken = result.csrfToken;
        state.ready = true;
        return state.user;
      }
      function load() {
        if (!pending) {
          pending = api
            .get('/auth/session')
            .then(apply)
            .finally(() => {
              pending = null;
            });
        }
        return pending;
      }
      return {
        state,
        load,
        ensure: load,
        login(username, password) {
          return load()
            .then(() => api.post('/auth/login', { username, password }))
            .then(apply);
        },
        logout() {
          return api.post('/auth/logout', {}).then(() => {
            clearPrivateState();
            state.user = null;
            state.csrfToken = '';
            state.ready = false;
          });
        },
      };
    },
  ]);
