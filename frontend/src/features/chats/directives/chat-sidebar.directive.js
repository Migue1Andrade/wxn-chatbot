import angular from 'angular';
import template from '../templates/chat-sidebar.html?raw';

angular.module('wxn.chats').directive('chatSidebar', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: {
      state: '<',
      socket: '<',
      member: '<',
      query: '=',
      onSelect: '&',
      onRetry: '&',
      onFilter: '&',
      onScope: '&',
      isAdmin: '<',
      onResponsible: '&',
      onSearch: '&',
      onMore: '&',
      filters: '<',
      contextError: '<',
      onReconnect: '&',
    },
    controller: function () {
      this.scopeFilter = (scope) => (scope === undefined ? this.state.scope : this.onScope({ scope }));
      this.statusFilter = (filter) => (filter === undefined ? this.state.filter : this.onFilter({ filter }));
      this.availableFilters = () =>
        this.filters.filter(
          (filter) => filter.value !== 'unassigned' || (this.isAdmin && this.state.scope === 'all'),
        );
      this.memberName = (id) =>
        id == null
          ? 'Não atribuído'
          : this.member.members.find((m) => m.id === id)?.name || 'Responsável #' + id;
    },
    controllerAs: '$ctrl',
    template,
  };
});
