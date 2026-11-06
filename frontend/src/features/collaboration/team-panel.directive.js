import angular from 'angular';
import template from './team-panel.html?raw';
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
        function flushDraft() {
          $timeout.cancel(draftTimer);
          if (draftDirty && previousConversation && vm.tab === 'internal-messages' && !vm.editing) {
            drafts.save(previousConversation, 'INTERNAL', vm.text || '').catch(error);
          }
          draftDirty = false;
        }
        vm.changed = () => {
          if (vm.tab !== 'internal-messages' || vm.editing) {
            return;
          }
          draftDirty = true;
          $timeout.cancel(draftTimer);
          draftTimer = $timeout(flushDraft, 700);
        };
        function loadDraft() {
          if (vm.tab !== 'internal-messages') {
            return;
          }
          const id = vm.conversation.id;
          drafts
            .load(id, 'INTERNAL')
            .then((draft) => {
              if (vm.conversation?.id === id && vm.tab === 'internal-messages' && !vm.text) {
                vm.text = draft.content;
              }
            })
            .catch(error);
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
          vm.error = '';
          vm.page = more ? vm.page + 1 : 0;
          return api
            .get(root(), { page: vm.page })
            .then((rows) => {
              if (token !== epoch) {
                return;
              }
              vm.more = rows.length === 30;
              vm.rows = more ? vm.rows.concat(rows) : rows;
            })
            .catch(error)
            .finally(() => {
              if (token === epoch) {
                vm.loading = false;
              }
            });
        };
        vm.changeTab = (tab) => {
          flushDraft();
          vm.tab = tab;
          vm.text = '';
          vm.editing = null;
          operationId = null;
          vm.load();
          loadDraft();
        };
        vm.canEdit = (entry) =>
          entry.authorUserId === account.user?.id ||
          (entry.kind === 'NOTE' && account.user?.role === 'ADMIN');
        vm.edit = (entry) => {
          vm.editing = entry;
          vm.text = entry.content;
        };
        vm.save = () => {
          if (vm.busy || !vm.text?.trim()) {
            return;
          }
          vm.busy = true;
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
              })
            : api.post(path, { content: vm.text, operationId });
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
                operationId = null;
                vm.load();
              }
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
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
