import angular from 'angular';
import navigationTemplate from './navigation.html?raw';
angular.module('wxn.members').directive('managementNavigation', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { onFilter: '&?' },
    controllerAs: '$ctrl',
    template: navigationTemplate,
    controller: [
      '$scope',
      '$location',
      'ChatStateService',
      'DevMemberContextService',
      'AccountSessionService',
      'SocketService',
      function ($scope, $location, chatState, memberContext, session, socket) {
        this.account = session.state;
        this.navOpen = false;
        this.closeNavigation = () => {
          this.navOpen = false;
        };
        const offRoute = $scope.$on('$routeChangeSuccess', this.closeNavigation);
        $scope.$on('$destroy', offRoute);
        this.logout = () =>
          session.logout().then(() => {
            socket.disconnect();
            $location.path('/login');
          });
        this.isSettings = $location.path().startsWith('/settings');
        this.isKanban = $location.path() === '/kanban';
        this.isDashboard = $location.path() === '/dashboard';
        this.isTasks = $location.path() === '/tasks';
        this.isReports = $location.path() === '/reports';
        this.isMembers = $location.path() === '/members';
        this.isChat = $location.path() === '/';
        this.chat = chatState.state;
        this.member = memberContext.state;
        this.openConversations = (filter) => {
          this.closeNavigation();
          if (this.isChat && this.onFilter) {
            this.onFilter({ filter });
            return;
          }
          this.chat.filter = filter;
          if (filter === 'mine') {
            this.chat.responsibleId = null;
          }
          $location.path('/');
        };
      },
    ],
  };
});
