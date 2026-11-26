import { visibleConversationStatus } from '../chats/conversation-filters.js';
import angular from 'angular';
import template from './dashboard.html?raw';
import './dashboard.css';
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
      'ChannelsService',
      function (service, $scope, $timeout, socket, bus, chats, messages, app, channels) {
        const vm = this;
        let timer;
        let destroyed = false;
        const labels = {
          PENDING: 'Aguardando triagem',
          IN_PROGRESS: 'Em atendimento',
          RESOLVED: 'Resolvidos',
          CLOSED: 'Encerrados',
        };
        let requestId = 0;
        vm.period = '7';
        vm.filters = { channelId: '' };
        vm.channels = [];
        channels
          .listChannels()
          .then((rows) => {
            vm.channels = rows || [];
          })
          .catch(() => {
            vm.channels = [];
          });
        vm.setPeriod = (period) => {
          vm.period = period;
          if (period !== 'custom') {
            vm.filters.from = null;
            vm.filters.to = null;
            vm.load();
          }
        };
        vm.applyCustomPeriod = () => {
          if (!vm.filters.from || !vm.filters.to || vm.filters.to < vm.filters.from) {
            vm.error = 'Informe um intervalo de datas válido.';
            return;
          }
          vm.load();
        };
        vm.load = () => {
          const id = ++requestId;
          const params = {};
          if (vm.period === 'custom') {
            params.from = vm.filters.from;
            params.to = vm.filters.to;
          } else {
            const days = Number(vm.period || 7);
            const end = new Date();
            const start = new Date(end);
            start.setDate(start.getDate() - days + 1);
            params.from = start.toLocaleDateString('en-CA');
            params.to = end.toLocaleDateString('en-CA');
          }
          if (vm.filters.channelId) {
            params.channelId = vm.filters.channelId;
          }
          vm.loading = true;
          vm.error = null;
          service
            .overview(params)
            .then((result) => {
              if (!destroyed && id === requestId) {
                const counts = new Map(Object.entries(labels).map(([status]) => [status, 0]));
                result.groups.forEach((group) => {
                  const status = visibleConversationStatus(group.status);
                  counts.set(status, (counts.get(status) || 0) + group.count);
                });
                vm.groups = Array.from(counts, ([status, count]) => ({
                  status,
                  count,
                  label: labels[status] || status,
                }));
                vm.volume = (result.dailyVolume || []).map((day) => ({
                  ...day,
                  label: day.date.slice(8) + '/' + day.date.slice(5, 7),
                }));
                vm.hasVolume = vm.volume.some((day) => day.opened || day.resolved);
                vm.volumeMax = Math.max(1, ...vm.volume.flatMap((day) => [day.opened, day.resolved]));
                vm.aging = [
                  { label: 'Menos de 15 min', count: result.replyAging?.under15 || 0, tone: 'ops-neutral' },
                  {
                    label: 'De 15 a menos de 60 min',
                    count: result.replyAging?.between15And60 || 0,
                    tone: 'ops-warning',
                  },
                  { label: '60 min ou mais', count: result.replyAging?.over60 || 0, tone: 'ops-danger' },
                ];
                vm.agingTotal = vm.aging.reduce((sum, bucket) => sum + bucket.count, 0);
                vm.metrics = result;
                vm.total = vm.groups.reduce((sum, group) => sum + group.count, 0);
              }
            })
            .catch((e) => {
              if (!destroyed && id === requestId) {
                vm.error = e.userMessage || 'Não foi possível atualizar os indicadores.';
              }
            })
            .finally(() => {
              if (!destroyed && id === requestId) {
                vm.loading = false;
              }
            });
        };
        const off = [
          'wxn:kanban:metrics',
          chats.CREATED,
          chats.UPDATED,
          chats.CLOSED,
          chats.ASSIGNED,
          chats.TRANSFERRED,
          chats.REOPENED,
          messages.RECEIVED,
          messages.SENT,
          messages.STATUS_UPDATED,
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
