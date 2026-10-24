import angular from 'angular';
import { ConversationFilters, queryParameters } from './conversation-filters.js';

angular.module('wxn.chats').controller('ChatsController', [
  '$scope',
  '$timeout',
  '$q',
  'ChatsService',
  'ChatStateService',
  'SocketService',
  'EventBusService',
  'ChatEvents',
  'MessageEvents',
  'AppEvents',
  'MemberEvents',
  'DevMemberContextService',
  'ChannelsService',
  function (
    $scope,
    $timeout,
    $q,
    chats,
    store,
    socket,
    bus,
    chatEvents,
    messageEvents,
    appEvents,
    memberEvents,
    memberContext,
    channels,
  ) {
    const vm = this;
    vm.state = store.state;
    vm.socket = socket.state;
    vm.member = memberContext.state;
    vm.query = '';
    vm.filters = ConversationFilters;
    vm.channels = [];
    vm.mobileOpen = false;
    vm.contextError = vm.member.error;
    let destroyed = false;
    let generation = 0;
    let refreshTimer;
    let searchTimer;
    let loadedPages = 1;
    vm.channelName = (id) => vm.channels.find((channel) => channel.id === id)?.name || 'Canal indisponível';
    vm.channelActive = (id) =>
      vm.channels.some((channel) => channel.id === id && channel.status === 'ACTIVE');
    vm.select = (conversation) => {
      store.select(conversation);
      vm.mobileOpen = true;
      bus.emit(chatEvents.SELECTED, conversation);
    };
    vm.reconnect = () => socket.connect();
    vm.back = () => {
      vm.mobileOpen = false;
    };
    function resetQuery() {
      generation++;
      loadedPages = 1;
      store.resetQuery();
      vm.refresh();
    }
    vm.changeFilter = (filter) => {
      vm.state.filter = filter;
      if (filter === 'mine' || filter === 'unassigned') vm.state.responsibleId = null;
      resetQuery();
    };
    vm.search = () => {
      generation++;
      $timeout.cancel(searchTimer);
      $timeout.cancel(refreshTimer);
      vm.state.loading = true;
      searchTimer = $timeout(() => {
        vm.state.search = vm.query;
        resetQuery();
      }, 300);
    };
    vm.changeResponsible = () => resetQuery();
    vm.changeReader = (id) => memberContext.choose(id);
    vm.refresh = function () {
      if (destroyed) return;
      if (vm.state.filter === 'mine' && !vm.state.readerId) {
        vm.state.loading = false;
        store.replace([], store.revision());
        return;
      }
      const token = ++generation;
      const requestedAt = store.revision();
      const filters = queryParameters(vm.state);
      vm.state.loading = true;
      vm.state.error = null;
      const rows = [];
      function load(page) {
        return chats.listConversations(filters, page).then((result) => {
          if (destroyed || token !== generation) return;
          rows.push(...result.items);
          if (result.hasMore && page + 1 < loadedPages) return load(page + 1);
          store.replace(rows, requestedAt);
          vm.state.hasMore = result.hasMore;
          vm.state.nextPage = result.nextPage;
        });
      }
      return load(0)
        .catch((error) => {
          if (token === generation) vm.state.error = error.userMessage;
        })
        .finally(() => {
          if (token === generation) vm.state.loading = false;
        });
    };
    vm.loadMore = () => {
      if (!vm.state.hasMore || vm.state.loading || vm.state.loadingMore) return;
      const token = generation;
      const requestedAt = store.revision();
      vm.state.loadingMore = true;
      chats
        .listConversations(queryParameters(vm.state), vm.state.nextPage)
        .then((result) => {
          if (destroyed || token !== generation) return;
          store.replace([...vm.state.conversations, ...result.items], requestedAt);
          loadedPages++;
          vm.state.hasMore = result.hasMore;
          vm.state.nextPage = result.nextPage;
        })
        .catch((error) => {
          if (token === generation) vm.state.error = error.userMessage;
        })
        .finally(() => {
          vm.state.loadingMore = false;
        });
    };
    function scheduleRefresh() {
      $timeout.cancel(refreshTimer);
      refreshTimer = $timeout(() => {
        vm.state.search = vm.query;
        vm.refresh();
      }, 160);
    }
    function conversationEvent(event) {
      store.applyEvent(event.payload);
      scheduleRefresh();
    }
    function readerChanged() {
      vm.state.readerId = vm.member.member?.id || null;
      vm.state.unread = {};
      resetQuery();
    }
    function synchronizeContext() {
      return $q
        .all([
          memberContext.load(),
          channels.listChannels().then((rows) => {
            vm.channels = rows;
          }),
        ])
        .then(() => {
          vm.contextError = vm.member.error;
          vm.state.readerId = vm.member.member?.id || null;
          vm.refresh();
        })
        .catch((error) => {
          vm.contextError = error.userMessage;
          vm.refresh();
        });
    }
    const cleanup = [
      chatEvents.CREATED,
      chatEvents.UPDATED,
      chatEvents.ASSIGNED,
      chatEvents.TRANSFERRED,
      chatEvents.CLOSED,
      chatEvents.REOPENED,
    ].map((type) => bus.on(type, conversationEvent));
    cleanup.push(
      bus.on(chatEvents.READ_UPDATED, (event) => store.read(event.payload)),
      bus.on(messageEvents.RECEIVED, scheduleRefresh),
      bus.on(messageEvents.SENT, scheduleRefresh),
      bus.on(memberEvents.UPDATED, readerChanged),
      bus.on(appEvents.SOCKET_CONNECTED, () => {
        if (vm.state.selected)
          chats
            .getConversation(vm.state.selected.id)
            .then(store.applyEvent)
            .catch((error) => {
              vm.state.error = error.userMessage;
            });
        synchronizeContext();
      }),
    );
    socket.connect();
    synchronizeContext();
    $scope.$on('$destroy', () => {
      destroyed = true;
      generation++;
      $timeout.cancel(refreshTimer);
      $timeout.cancel(searchTimer);
      cleanup.forEach((unsubscribe) => unsubscribe());
      socket.disconnect();
    });
  },
]);
