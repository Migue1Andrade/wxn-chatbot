import angular from 'angular';
import template from './team-panel.html?raw';
import { requestedTeamPage } from './team-pagination.js';
angular.module('wxn.chats').directive('teamPanel', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversation: '<', onClose: '&?' },
    controllerAs: 'team',
    template,
    controller: [
      'ConversationDraftService',
      '$timeout',
      'ApiClientService',
      'AccountSessionState',
      'EventBusService',
      '$scope',
      function (drafts, $timeout, api, account, bus, $scope) {
        const vm = this;
        vm.tab = 'notes';
        vm.rows = [];
        vm.page = 0;
        vm.account = account;
        let epoch = 0;
        let operationId;
        let signature;
        let draftTimer;
        let previousConversation;
        let draftDirty = false;
        vm.draftState = '';
        vm.loadError = '';
        vm.writeError = '';
        vm.colors = [
          { id: 'SAGE', name: 'Sálvia', swatch: '#e8f3e8' },
          { id: 'SKY', name: 'Céu', swatch: '#e6f2fb' },
          { id: 'LAVENDER', name: 'Lavanda', swatch: '#f0eafa' },
          { id: 'PEACH', name: 'Pêssego', swatch: '#fff0e5' },
          { id: 'SUNSHINE', name: 'Sol', swatch: '#fff5d8' },
          { id: 'ROSE', name: 'Rosa', swatch: '#fbe9ed' },
        ];
        vm.selectedColor = 'SAGE';
        vm.retryLoad = () => vm.load();
        vm.retrySave = () => vm.save();
        function flushDraft() {
          $timeout.cancel(draftTimer);
          if (draftDirty && previousConversation && vm.tab === 'internal-messages' && !vm.editing) {
            const id = previousConversation;
            const text = vm.text || '';
            drafts
              .save(id, 'INTERNAL', text)
              .then(() => {
                if (vm.conversation?.id === id) {
                  vm.draftState = 'Rascunho salvo';
                }
              })
              .catch(() => {
                if (vm.conversation?.id === id) {
                  draftDirty = true;
                  vm.draftState = 'Não foi possível salvar o rascunho.';
                }
              });
          }
          draftDirty = false;
        }
        vm.changed = () => {
          if (vm.tab !== 'internal-messages' || vm.editing) {
            return;
          }
          draftDirty = true;
          vm.draftState = 'Salvando rascunho…';
          $timeout.cancel(draftTimer);
          draftTimer = $timeout(flushDraft, 700);
        };
        function loadDraft() {
          if (vm.tab !== 'internal-messages') {
            return;
          }
          const id = vm.conversation.id;
          const token = epoch;
          drafts
            .load(id, 'INTERNAL')
            .then((draft) => {
              if (
                token === epoch &&
                vm.conversation?.id === id &&
                vm.tab === 'internal-messages' &&
                !vm.text
              ) {
                vm.text = draft.content;
              }
            })
            .catch(() => {
              if (token === epoch && vm.conversation?.id === id) {
                vm.draftState = 'Não foi possível carregar o rascunho.';
              }
            });
        }
        const error = (e) => {
          vm.error = e.userMessage || 'Não foi possível concluir a operação.';
        };
        const root = () => '/conversations/' + vm.conversation.id + '/' + vm.tab;
        vm.$onChanges = () => {
          if (previousConversation === vm.conversation?.id) {
            return;
          }
          flushDraft();
          previousConversation = vm.conversation?.id;
          epoch++;
          vm.text = '';
          vm.editing = null;
          vm.load();
          loadDraft();
        };
        vm.load = (more = false) => {
          if (!vm.conversation) {
            return;
          }
          const token = ++epoch;
          vm.loading = true;
          vm.loadError = '';
          const requestedPage = requestedTeamPage(vm.page, more);
          return api
            .get(root(), { page: requestedPage })
            .then((rows) => {
              if (token !== epoch) {
                return;
              }
              vm.page = requestedPage;
              vm.more = rows.length === 30;
              if (vm.tab === 'internal-messages') {
                const known = new Set(vm.rows.map((entry) => entry.id));
                const chronological = rows
                  .filter((entry) => !more || !known.has(entry.id))
                  .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));
                vm.rows = more ? chronological.concat(vm.rows) : chronological;
              } else {
                vm.rows = more
                  ? vm.rows.concat(rows.filter((entry) => !vm.rows.some((old) => old.id === entry.id)))
                  : rows;
              }
            })
            .catch((e) => {
              if (token === epoch) {
                vm.loadError = e.userMessage || 'Não foi possível carregar a colaboração.';
              }
            })
            .finally(() => {
              if (token === epoch) {
                vm.loading = false;
              }
            });
        };
        vm.changeTab = (tab) => {
          epoch++;
          flushDraft();
          vm.tab = tab;
          vm.text = '';
          vm.editing = null;
          operationId = null;
          vm.busy = false;
          vm.activeOperation = null;
          vm.load();
          loadDraft();
        };
        vm.canEdit = (entry) =>
          entry.authorUserId === account.user?.id ||
          (entry.kind === 'NOTE' && account.user?.role === 'ADMIN');
        vm.initials = (name) =>
          (name || 'Equipe')
            .trim()
            .split(/\s+/)
            .slice(0, 2)
            .map((part) => part[0])
            .join('')
            .toUpperCase();
        vm.edit = (entry) => {
          vm.editing = entry;
          vm.text = entry.content;
          vm.selectedColor = entry.color || 'SAGE';
        };
        vm.save = () => {
          if (vm.busy || !vm.text?.trim()) {
            return;
          }
          vm.busy = true;
          const operation = crypto.randomUUID();
          vm.activeOperation = operation;
          vm.writeError = '';
          const conversationId = vm.conversation.id;
          const tab = vm.tab;
          const text = vm.text;
          const currentSignature = JSON.stringify([conversationId, tab, text]);
          if (signature !== currentSignature) {
            operationId = crypto.randomUUID();
            signature = currentSignature;
          }
          const path = vm.editing ? '/' + vm.tab + '/' + vm.editing.id : root();
          const promise = vm.editing
            ? api.patch(path, {
                content: vm.text,
                pinned: vm.editing.pinned,
                expectedVersion: vm.editing.version,
                color: vm.tab === 'notes' ? vm.selectedColor : vm.editing.color,
              })
            : api.post(path, {
                content: vm.text,
                operationId,
                color: vm.tab === 'notes' ? vm.selectedColor : undefined,
              });
          promise
            .then(() => {
              if (vm.conversation?.id === conversationId && vm.tab === tab && vm.text === text) {
                if (vm.tab === 'internal-messages' && !vm.editing) {
                  draftDirty = false;
                  $timeout.cancel(draftTimer);
                  drafts.save(vm.conversation.id, 'INTERNAL', '').catch(error);
                }
                vm.text = '';
                vm.editing = null;
                vm.selectedColor = 'SAGE';
                operationId = null;
                vm.load();
              }
            })
            .catch((e) => {
              if (vm.conversation?.id === conversationId && vm.tab === tab) {
                vm.writeError =
                  e.userMessage || 'Não foi possível salvar. Seu texto foi preservado; tente novamente.';
              }
            })
            .finally(() => {
              if (vm.activeOperation === operation) {
                vm.busy = false;
                vm.activeOperation = null;
              }
            });
        };
        vm.pin = (entry) =>
          api
            .patch('/notes/' + entry.id, {
              content: entry.content,
              pinned: !entry.pinned,
              expectedVersion: entry.version,
            })
            .then(() => vm.load())
            .catch(error);
        vm.remove = (entry) => {
          vm.deleting = entry;
        };
        vm.confirmDelete = () => {
          vm.busy = true;
          api
            .delete('/' + vm.tab + '/' + vm.deleting.id + '?version=' + vm.deleting.version)
            .then(() => {
              vm.deleting = null;
              return vm.load();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.history = (entry) =>
          api
            .get('/' + vm.tab + '/' + entry.id + '/history')
            .then((rows) => {
              vm.revisions = rows;
            })
            .catch(error);
        const off = bus.on('wxn:team:updated', (event) => {
          if (event.conversationId === vm.conversation?.id) {
            vm.load();
          }
        });
        $scope.$on('$destroy', () => {
          flushDraft();
          epoch++;
          off();
        });
      },
    ],
  };
});
