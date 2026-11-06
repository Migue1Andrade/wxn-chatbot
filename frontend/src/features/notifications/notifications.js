import angular from 'angular';
import template from './notifications.html?raw';
import bellTemplate from './notification-bell.html?raw';
angular
  .module('wxn.chats')
  .factory('NotificationCenterService', [
    '$q',
    'ApiClientService',
    'EventBusService',
    'AccountSessionState',
    function ($q, api, bus, account) {
      const state = { items: [], count: 0, error: '', page: 0 };
      let owner;
      let generation = 0;
      let pending;
      let pendingOwner;
      function load(more = false, force = false) {
        if (owner !== account.user?.id) {
          owner = account.user?.id;
          state.items = [];
          state.count = 0;
        }
        if (!owner) {
          return;
        }
        if (!more && !force && pending && pendingOwner === owner) {
          return pending;
        }
        pendingOwner = owner;
        state.page = more ? state.page + 1 : 0;
        const requestedOwner = owner;
        const token = ++generation;
        state.loading = true;
        state.error = '';
        pending = $q
          .all([
            api.get('/notifications', { page: state.page }).then((rows) => {
              if (token !== generation || requestedOwner !== owner || account.user?.id !== requestedOwner) {
                return;
              }
              state.items = more ? state.items.concat(rows) : rows;
              state.more = rows.length === 30;
            }),
            api.get('/notifications/unread-count').then((result) => {
              if (token === generation && requestedOwner === owner && account.user?.id === requestedOwner) {
                state.count = result.count;
              }
            }),
          ])
          .catch((e) => {
            if (token === generation) {
              state.error = e.userMessage;
            }
          })
          .finally(() => {
            if (token === generation) {
              state.loading = false;
              pending = null;
            }
          });
        return pending;
      }
      bus.on('wxn:notification:created', () => load(false, true));
      return {
        state,
        load,
        open: (row) => bus.emit('wxn:notification:open', row),
        read: (row) =>
          api
            .patch('/notifications/' + row.id + '/read', {})
            .then(() => {
              row.readAt = new Date().toISOString();
              return load(false, true);
            })
            .catch((e) => {
              state.error = e.userMessage;
            }),
        readAll: () =>
          api
            .post('/notifications/read-all', {})
            .then(() => load(false, true))
            .catch((e) => {
              state.error = e.userMessage;
            }),
      };
    },
  ])
  .directive('notificationBell', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'bell',
      template: bellTemplate,
      controller: [
        '$scope',
        '$element',
        '$location',
        '$timeout',
        'NotificationCenterService',
        'TasksService',
        'SocketService',
        'EventBusService',
        function ($scope, $element, $location, $timeout, center, tasks, socket, bus) {
          const vm = this;
          let epoch = 0;
          let focusTimer;
          const focus = (selector) => {
            $timeout.cancel(focusTimer);
            focusTimer = $timeout(() => $element[0].querySelector(selector)?.focus(), 0);
          };
          vm.back = () => {
            epoch++;
            vm.selected = null;
            vm.task = null;
            vm.loading = false;
            focus('.notification-item');
          };
          vm.state = center.state;
          vm.center = center;
          vm.close = () => {
            $timeout.cancel(focusTimer);
            epoch++;
            vm.open = false;
            vm.selected = null;
            vm.task = null;
            vm.error = '';
            vm.loading = false;
          };
          vm.toggle = () => {
            if (vm.open) {
              vm.close();
              return;
            }
            vm.open = true;
            center.load();
          };
          vm.select = (row) => {
            vm.selected = row;
            focus('.notification-back');
            vm.task = null;
            vm.error = '';
            vm.loading = false;
            const token = ++epoch;
            if (row.resourceType !== 'TASK') {
              return;
            }
            vm.loading = true;
            tasks
              .get(row.resourceId)
              .then((task) => {
                if (token === epoch && vm.selected === row) {
                  vm.task = task;
                }
              })
              .catch((e) => {
                if (token === epoch) {
                  vm.error = e.userMessage || 'Não foi possível abrir esta atividade.';
                }
              })
              .finally(() => {
                if (token === epoch) {
                  vm.loading = false;
                }
              });
          };
          vm.complete = () => {
            if (vm.busy || !vm.task) {
              return;
            }
            vm.busy = true;
            const selected = vm.selected;
            const currentTask = vm.task;
            tasks
              .transition(currentTask, 'COMPLETED')
              .then((task) => {
                if (vm.selected === selected) {
                  vm.task = task;
                }
                return center.read(selected);
              })
              .catch((e) => {
                vm.error = e.userMessage || 'Não foi possível concluir a atividade.';
              })
              .finally(() => {
                vm.busy = false;
              });
          };
          vm.go = (path, query = {}) => {
            vm.close();
            $location.path(path).search(query);
          };
          const outside = (event) => {
            if (vm.open && !$element[0].contains(event.target)) {
              $scope.$evalAsync(vm.close);
            }
          };
          document.addEventListener('click', outside);
          const off = $scope.$on('$routeChangeSuccess', vm.close);
          const offOpen = bus.on('wxn:notification:open', (row) => {
            vm.open = true;
            vm.select(row);
          });
          $scope.$on('$destroy', () => {
            $timeout.cancel(focusTimer);
            epoch++;
            off();
            offOpen();
            document.removeEventListener('click', outside);
          });
          center.load();
          socket.connect();
        },
      ],
    };
  })
  .directive('notificationsPage', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'vm',
      template,
      controller: [
        'NotificationCenterService',
        'SocketService',
        function (center, socket) {
          this.center = center;
          this.state = center.state;
          center.load();
          socket.connect();
        },
      ],
    };
  });
