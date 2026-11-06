import angular from 'angular';
import { operationalEventLabel } from '../chats/operational-event-label.js';
import template from './kanban.html?raw';
angular
  .module('wxn.chats')
  .directive('kanbanPage', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'vm',
      template,
      controller: [
        '$scope',
        '$timeout',
        '$interval',
        '$q',
        '$location',
        'AccountSessionState',
        'KanbanService',
        'MembersService',
        'ChannelsService',
        'ManagementService',
        'DevMemberContextService',
        'SocketService',
        'EventBusService',
        'ChatEvents',
        'MessageEvents',
        'AppEvents',
        function (
          $scope,
          $timeout,
          $interval,
          $q,
          $location,
          account,
          service,
          members,
          channels,
          management,
          context,
          socket,
          bus,
          chats,
          messages,
          app,
        ) {
          const vm = this;
          vm.columns = [
            ['PENDING', 'Aguardando triagem'],
            ['IN_PROGRESS', 'Em atendimento'],
            ['WAITING_CUSTOMER', 'Aguardando cliente'],
            ['RESOLVED', 'Resolvidos'],
            ['CLOSED', 'Encerrados'],
          ].map(([status, name]) => ({ status, name, items: [], total: 0, page: 0 }));
          vm.slaLabels = {
            NOT_CONFIGURED: 'Sem SLA configurado',
            NOT_APPLICABLE: 'SLA indisponível',
            ON_TRACK: 'SLA no prazo',
            AT_RISK: 'SLA em risco',
            BREACHED: 'SLA violado',
            COMPLETED: 'SLA concluído',
          };
          vm.priorities = { LOW: 'Baixa', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente' };
          vm.filters = {
            search: '',
            priority: '',
            assignedMemberId: '',
            unassigned: false,
            sort: 'ATTENTION',
            ...$location.search(),
          };
          vm.layout = 'board';
          vm.mobileStatus = 'PENDING';
          vm.visibleCount = () => vm.columns.reduce((sum, column) => sum + column.items.length, 0);
          vm.statusName = (status) => vm.columns.find((column) => column.status === status)?.name || status;
          vm.views = [];
          vm.members = [];
          vm.channels = [];
          vm.departments = [];
          vm.channelName = (id) =>
            vm.channels.find((channel) => channel.id === id)?.name || 'Canal indisponível';
          vm.departmentName = (id) => vm.departments.find((department) => department.id === id)?.name;
          vm.now = Date.now();
          vm.context = context.state;
          vm.account = account;
          let destroyed = false;
          let epoch = 0;
          let refreshTimer;
          let searchTimer;
          const error = (e) => {
            vm.error = e.userMessage || 'Não foi possível concluir a operação.';
          };
          vm.memberName = (id) =>
            vm.members.find((m) => m.id === id)?.name || (id ? 'Membro indisponível' : 'Não atribuído');
          vm.eventLabel = (event) => operationalEventLabel(event, vm.memberName);
          vm.initials = (name) =>
            (name || '?')
              .split(/\s+/)
              .slice(0, 2)
              .map((part) => part[0])
              .join('')
              .toUpperCase();
          vm.duration = (timestamp) => {
            if (!timestamp) {
              return 'Indisponível';
            }
            const minutes = Math.max(0, Math.floor((vm.now - Date.parse(timestamp)) / 60000));
            if (minutes < 60) {
              return minutes + ' min';
            }
            if (minutes < 1440) {
              return Math.floor(minutes / 60) + 'h ' + (minutes % 60) + 'min';
            }
            return Math.floor(minutes / 1440) + 'd ' + Math.floor((minutes % 1440) / 60) + 'h';
          };
          vm.resolution = (card) => {
            if (!card.resolvedAt || !card.cycleStartedAt) {
              return 'Tempo de resolução indisponível';
            }
            return (
              'Resolvido em ' +
              Math.max(
                0,
                Math.round((Date.parse(card.resolvedAt) - Date.parse(card.cycleStartedAt)) / 60000),
              ) +
              ' min'
            );
          };
          function params(column) {
            const filters = Object.fromEntries(
              Object.entries(vm.filters).filter(([, value]) => value !== '' && value !== false),
            );
            return { ...filters, status: column.status, page: column.page, size: 25 };
          }
          vm.load = () => {
            const token = ++epoch;
            vm.loading = true;
            vm.error = '';
            $location.search(
              Object.fromEntries(
                Object.entries(vm.filters).filter(([, value]) => value !== '' && value !== false),
              ),
            );
            return $q
              .all(
                vm.columns
                  .map((column) => {
                    column.page = 0;
                    return service.page(params(column)).then((result) => {
                      if (token !== epoch || destroyed) {
                        return;
                      }
                      Object.assign(column, result, { items: result.items });
                    });
                  })
                  .concat([
                    service.metrics().then((result) => {
                      if (token === epoch && !destroyed) {
                        vm.metrics = result;
                      }
                    }),
                  ]),
              )
              .catch(error)
              .finally(() => {
                if (token === epoch) {
                  vm.loading = false;
                }
              });
          };
          vm.more = (column) => {
            if (column.loading) {
              return;
            }
            const token = epoch;
            column.loading = true;
            column.page++;
            service
              .page(params(column))
              .then((result) => {
                if (token === epoch && !destroyed) {
                  column.items.push(...result.items);
                  column.hasMore = result.hasMore;
                }
              })
              .catch((e) => {
                column.page--;
                error(e);
              })
              .finally(() => {
                column.loading = false;
              });
          };
          vm.search = () => {
            $timeout.cancel(searchTimer);
            searchTimer = $timeout(vm.load, 300);
          };
          vm.clear = () => {
            vm.filters = {
              search: '',
              priority: '',
              assignedMemberId: '',
              unassigned: false,
              sort: 'ATTENTION',
            };
            vm.load();
          };
          vm.open = (card) => {
            vm.detail = card;
            vm.targetMember = card.conversation.assignedMemberId;
            vm.targetStatus = card.conversation.status;
            vm.targetPriority = card.priority;
            vm.history = [];
            const id = card.conversation.id;
            service
              .details(id)
              .then((result) => {
                if (vm.detail?.conversation.id === id) {
                  vm.detail = result;
                }
              })
              .catch(error);
            service
              .history(id)
              .then((rows) => {
                if (vm.detail?.conversation.id === id) {
                  vm.history = rows;
                }
              })
              .catch(error);
          };
          vm.actor = () => account.user?.memberId;
          function mutate(operation) {
            vm.busy = true;
            vm.error = '';
            return operation
              .then(() => {
                if (vm.detail) {
                  vm.open(vm.detail);
                }
                return vm.load();
              })
              .catch((e) => {
                vm.load().finally(() => error(e));
              })
              .finally(() => {
                vm.busy = false;
              });
          }
          vm.move = (card, status) => {
            if (vm.busy || status === card.conversation.status) {
              return;
            }
            if (!vm.actor()) {
              vm.error = 'Sua conta precisa de um Member HUMAN ativo para registrar a operação.';
              return;
            }
            vm.confirmation = { card, status };
          };
          vm.confirmMove = () => {
            const { card, status } = vm.confirmation;
            vm.confirmation = null;
            mutate(service.status(card, status, vm.actor()));
          };
          vm.changePriority = () => mutate(service.priority(vm.detail, vm.targetPriority));
          vm.assign = () => {
            if (!vm.actor() || !vm.targetMember) {
              vm.error = 'Selecione responsável e ator humano ativo.';
              return;
            }
            vm.assignmentConfirmation = true;
          };
          vm.confirmAssign = () => {
            vm.assignmentConfirmation = false;
            mutate(service.assign(vm.detail, Number(vm.targetMember), vm.actor()));
          };
          vm.saveView = () => {
            if (!vm.viewName?.trim()) {
              return;
            }
            mutate(
              service
                .saveView(vm.viewName.trim(), { filters: vm.filters, layout: vm.layout })
                .then(() => service.views())
                .then((views) => {
                  vm.views = views;
                  vm.viewName = '';
                  vm.savingView = false;
                }),
            );
          };
          vm.chooseView = () => {
            const view = vm.views.find((v) => v.id === Number(vm.selectedView));
            if (!view) {
              vm.clear();
              return;
            }
            try {
              const config = JSON.parse(view.configuration);
              vm.filters = { ...config.filters };
              vm.layout = config.layout === 'list' ? 'list' : 'board';
              vm.load();
            } catch {
              vm.error = 'Configuração de visualização inválida.';
            }
          };
          vm.deleteView = () => {
            if (vm.selectedView) {
              mutate(
                service
                  .deleteView(vm.selectedView)
                  .then(() => service.views())
                  .then((views) => {
                    vm.views = views;
                    vm.selectedView = '';
                  }),
              );
            }
          };
          const schedule = () => {
            $timeout.cancel(refreshTimer);
            refreshTimer = $timeout(vm.load, 400);
          };
          const unsubscribe = [
            'wxn:kanban:metrics',
            chats.CREATED,
            chats.UPDATED,
            chats.ASSIGNED,
            chats.TRANSFERRED,
            chats.CLOSED,
            chats.REOPENED,
            messages.RECEIVED,
            messages.SENT,
            app.SOCKET_CONNECTED,
          ].map((name) => bus.on(name, schedule));
          const clock = $interval(() => {
            vm.now = Date.now();
          }, 30000);
          const keydown = (event) => {
            if (
              event.key === 'Escape' &&
              !event.defaultPrevented &&
              !vm.confirmation &&
              !vm.assignmentConfirmation &&
              !vm.savingView
            ) {
              $scope.$evalAsync(() => {
                vm.detail = null;
              });
            }
          };
          document.addEventListener('keydown', keydown);
          $q.all([
            context.load(),
            channels.listChannels().then((rows) => {
              vm.channels = rows;
            }),
            management.departments().then((rows) => {
              vm.departments = rows;
            }),
            members.listMembers().then((rows) => {
              vm.members = rows;
            }),
            service.views().then((views) => {
              vm.views = views;
            }),
          ])
            .then(vm.load)
            .catch(error);
          socket.connect();
          $scope.$on('$destroy', () => {
            destroyed = true;
            epoch++;
            $timeout.cancel(refreshTimer);
            $timeout.cancel(searchTimer);
            $interval.cancel(clock);
            unsubscribe.forEach((off) => off());
            document.removeEventListener('keydown', keydown);
          });
        },
      ],
    };
  })
  .directive('kanbanDrop', function () {
    return {
      restrict: 'A',
      scope: { kanbanDrop: '&' },
      link(scope, element) {
        const over = (event) => {
          event.preventDefault();
          element.addClass('is-drop-target');
        };
        const leave = () => element.removeClass('is-drop-target');
        const drop = (event) => {
          event.preventDefault();
          leave();
          try {
            const card = JSON.parse(event.dataTransfer.getData('application/wxn-card'));
            scope.$apply(() => scope.kanbanDrop({ card }));
          } catch {
            /* Ignore files and foreign drags. */
          }
        };
        element.on('dragover', over);
        element.on('dragleave', leave);
        element.on('drop', drop);
        scope.$on('$destroy', () => {
          element.off('dragover', over);
          element.off('dragleave', leave);
          element.off('drop', drop);
        });
      },
    };
  })
  .directive('kanbanDrag', function () {
    return {
      restrict: 'A',
      scope: { kanbanDrag: '<' },
      link(scope, element) {
        element.attr('draggable', 'true');
        const drag = (event) => {
          event.dataTransfer.setData('application/wxn-card', JSON.stringify(scope.kanbanDrag));
          event.dataTransfer.effectAllowed = 'move';
        };
        element.on('dragstart', drag);
        scope.$on('$destroy', () => element.off('dragstart', drag));
      },
    };
  });
