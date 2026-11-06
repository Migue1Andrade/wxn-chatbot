import angular from 'angular';
import template from './channels-page.html?raw';
angular.module('wxn.channels').directive('channelsPage', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      '$scope',
      '$timeout',
      'ChannelsService',
      'EventBusService',
      'SocketService',
      function ($scope, $timeout, channels, bus, socket) {
        const vm = this;
        let timer;
        let epoch = 0;
        vm.mockPhone = '+12025550100';
        vm.rows = [];
        vm.labels = {
          NOT_CONFIGURED: 'Não configurado',
          CONNECTED: 'Conectado',
          DISCONNECTED: 'Desconectado',
          UNKNOWN: 'Não confirmado',
          DEGRADED: 'Instável',
        };
        vm.capabilities = {
          SEND_TEXT: 'Texto',
          REPLY_TEXT: 'Resposta citada',
          EDIT_TEXT: 'Edição',
          FORWARD_TEXT: 'Encaminhamento',
          SEND_REACTION: 'Reações',
          REMOVE_REACTION: 'Remover reação',
          MARK_READ: 'Leitura remota',
          MESSAGE_STATUS: 'Status',
          CONNECTION_STATUS: 'Diagnóstico',
          QR_CODE: 'QR Code',
          CONFIGURE_WEBHOOKS: 'Configurar callbacks',
          RESTART_INSTANCE: 'Reinício',
          DISCONNECT_INSTANCE: 'Desconexão',
          DELETE_FOR_EVERYONE: 'Exclusão',
        };
        const error = (e) => {
          vm.error = e.userMessage || 'Não foi possível concluir a operação.';
        };
        vm.load = () => {
          vm.loading = true;
          vm.error = '';
          return channels
            .listChannels()
            .then((rows) => {
              vm.rows = rows;
            })
            .catch(error)
            .finally(() => {
              vm.loading = false;
            });
        };
        vm.close = () => {
          epoch++;
          $timeout.cancel(timer);
          vm.channel = null;
          vm.webhookRows = null;
          vm.lastInbound = null;
          vm.fromApi = false;
          vm.fromMe = false;
          vm.notice = '';
          vm.diagnostic = null;
          vm.qr = null;
          vm.confirmation = null;
          vm.error = '';
        };
        vm.inspect = (channel) => {
          vm.close();
          vm.channel = channel;
          vm.check();
        };
        vm.check = () => {
          if (!vm.channel) {
            return;
          }
          const id = vm.channel.id;
          const token = epoch;
          vm.checking = true;
          return channels
            .diagnostic(id)
            .then((result) => {
              if (token === epoch && vm.channel?.id === id) {
                vm.diagnostic = result;
                if (result.connection.state === 'CONNECTED') {
                  vm.qr = null;
                  $timeout.cancel(timer);
                }
              }
            })
            .catch(error)
            .finally(() => {
              if (token === epoch) {
                vm.checking = false;
              }
            });
        };
        vm.pair = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          vm.error = '';
          const id = vm.channel.id;
          const token = epoch;
          channels
            .qr(id)
            .then((result) => {
              if (token !== epoch) {
                return;
              }
              vm.qr = result;
              vm.polls = 0;
              const poll = () => {
                if (token !== epoch || !vm.qr || vm.polls >= 4) {
                  return;
                }
                timer = $timeout(() => {
                  vm.polls++;
                  vm.check().finally(poll);
                }, 15000);
              };
              poll();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.simulate = (repeat = false) => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          const input = repeat
            ? vm.lastInbound
            : {
                channelId: vm.channel.id,
                senderPhone: vm.mockPhone,
                content: vm.mockText,
                externalMessageId: 'mock-inbound-' + crypto.randomUUID(),
                senderName: 'Contato de demonstração',
              };
          if (!input) {
            vm.busy = false;
            return;
          }
          vm.lastInbound = input;
          channels
            .simulate(!!vm.fromMe, input, !!vm.fromApi)
            .then(() => {
              vm.notice = 'Evento Mock recebido. Consulte a Inbox e o diagnóstico para conferir o resultado.';
              return vm.check();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.scenario = () =>
          channels
            .scenario(vm.channel.id, vm.nextScenario)
            .then(() => {
              vm.notice = 'Cenário definido somente para o próximo envio neste processo.';
            })
            .catch(error);
        vm.reconcile = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          channels
            .reconcile(vm.channel.id, vm.correlation)
            .then(() => {
              vm.correlation = null;
              vm.notice = 'Reconciliação confirmada por evidência do callback.';
              return vm.check();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.webhooks = () =>
          channels
            .webhooks(vm.channel.id)
            .then((rows) => {
              vm.webhookRows = rows;
            })
            .catch(error);
        vm.retryWebhook = (row) =>
          channels.retryWebhook(vm.channel.id, row._id).then(vm.webhooks).catch(error);
        vm.prepare = (operation) => {
          vm.confirmation = { operation, operationId: crypto.randomUUID() };
        };
        vm.operate = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          vm.error = '';
          channels
            .operate(vm.channel.id, { ...vm.confirmation, confirmation: 'CONFIRM' })
            .then(() => {
              vm.confirmation = null;
              return vm.check();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.policy = () => {
          vm.busy = true;
          channels
            .policy(vm.channel.id, !vm.diagnostic.remoteReadEnabled)
            .then(vm.check)
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.create = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          channels
            .create(vm.form)
            .then(() => {
              vm.form = null;
              return vm.load();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.register = () => {
          vm.busy = true;
          channels
            .register(vm.channel.id, { operationId: vm.confirmation.operationId, confirmation: 'CONFIRM' })
            .then(() => {
              vm.confirmation = null;
              vm.notice = 'Cadastro de callbacks aceito. A entrega será confirmada no próximo callback.';
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        const off = bus.on('wxn:channel:changed', (event) => {
          if (vm.channel?.id === event.payload.channelId) {
            vm.check();
          }
        });
        $scope.$on('$destroy', () => {
          epoch++;
          $timeout.cancel(timer);
          off();
        });
        socket.connect();
        vm.load();
      },
    ],
  };
});
