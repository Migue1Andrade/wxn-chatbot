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
      onReader: '&',
      onResponsible: '&',
      onSearch: '&',
      onMore: '&',
      filters: '<',
      contextError: '<',
      onReconnect: '&',
    },
    controller: function () {
      this.$doCheck = () => {
        this.readerId = this.member.member?.id || null;
      };
      this.memberName = (id) => this.member.members.find((m) => m.id === id)?.name || 'Não atribuído';
    },
    controllerAs: '$ctrl',
    template,
  };
});
