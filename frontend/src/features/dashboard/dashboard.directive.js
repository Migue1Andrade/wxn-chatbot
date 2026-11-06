import angular from 'angular';
import template from './dashboard.html?raw';
angular.module('wxn.chats').directive('operationDashboard', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'KanbanService',
      '$scope',
      '$timeout',
      'SocketService',
      'EventBusService',
      'ChatEvents',
      'MessageEvents',
      'AppEvents',
      function (service, $scope, $timeout, socket, bus, chats, messages, app) {
        const vm = this;
        let timer;
        let destroyed = false;
        vm.labels = {
          PENDING: 'Aguardando triagem',
          IN_PROGRESS: 'Em atendimento',
          WAITING_CUSTOMER: 'Aguardando cliente',
          RESOLVED: 'Resolvidos',
          CLOSED: 'Encerrados',
        };
        vm.load = () => {
          vm.loading = true;
          service
            .metrics()
            .then((result) => {
              if (!destroyed) {
                vm.metrics = result;
                vm.total = result.groups.reduce((sum, group) => sum + group.count, 0);
              }
            })
            .catch((e) => {
              vm.error = e.userMessage;
            })
            .finally(() => {
              vm.loading = false;
            });
        };
        const off = [
          'wxn:kanban:metrics',
          chats.CREATED,
          chats.UPDATED,
          chats.CLOSED,
          chats.REOPENED,
          messages.RECEIVED,
          messages.SENT,
          app.SOCKET_CONNECTED,
        ].map((event) =>
          bus.on(event, () => {
            $timeout.cancel(timer);
            timer = $timeout(vm.load, 400);
          }),
        );
        vm.load();
        socket.connect();
        $scope.$on('$destroy', () => {
          destroyed = true;
          $timeout.cancel(timer);
          off.forEach((unsubscribe) => unsubscribe());
        });
      },
    ],
  };
});
