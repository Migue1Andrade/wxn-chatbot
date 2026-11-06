import angular from 'angular';
import template from '../templates/message-composer.html?raw';

angular.module('wxn.messages').directive('messageComposer', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { conversationId: '<', sending: '<', blocked: '<', reason: '@', memberName: '@' },
    controllerAs: '$ctrl',
    template,
    controller: [
      'ConversationDraftService',
      'ApiClientService',
      '$timeout',
      'EventBusService',
      'MessageEvents',
      'MessageActionsService',
      '$scope',
      function (drafts, api, $timeout, bus, events, actions, $scope) {
        const vm = this;
        vm.text = '';
        vm.actions = actions;
        vm.templates = [];
        vm.suggestions = [];
        vm.suggestionIndex = 0;
        let draftTimer;
        let dirty = false;
        let generation = 0;
        api
          .get('/quick-replies')
          .then((rows) => {
            vm.templates = rows.filter((row) => row.active);
          })
          .catch(() => {
            vm.templateError = 'Respostas rápidas indisponíveis.';
          });
        vm.variables = ['nome', 'primeiro_nome', 'telefone', 'atendente', 'protocolo', 'data', 'hora'].map(
          (name) => ({ shortcut: name, name: 'Variável: ' + name, scope: 'SYSTEM', content: '@@' + name }),
        );
        vm.changed = () => {
          dirty = true;
          const match = vm.text.match(/@@([a-zA-Z0-9_]*)$/);
          vm.suggestions = match
            ? vm.templates
                .concat(vm.variables)
                .filter((row) => row.shortcut.toLowerCase().includes(match[1].toLowerCase()))
                .slice(0, 8)
            : [];
          vm.suggestionIndex = 0;
          $timeout.cancel(draftTimer);
          const conversationId = vm.conversationId;
          const content = vm.text;
          draftTimer = $timeout(
            () =>
              drafts.save(conversationId, 'EXTERNAL', content).catch(() => {
                vm.draftError = 'Rascunho não sincronizado.';
              }),
            700,
          );
        };
        vm.insertTemplate = (template) => {
          const content = vm.text.replace(/@@[a-zA-Z0-9_]*$/, () => template.content);
          vm.suggestions = [];
          const token = generation;
          api
            .post('/quick-replies/preview', { conversationId: vm.conversationId, content })
            .then((result) => {
              if (token !== generation) {
                return;
              }
              vm.text = result.content;
              vm.templateError = result.missingVariables.length
                ? 'Preencha as variáveis sem valor: ' + result.missingVariables.join(', ')
                : '';
              vm.changed();
            })
            .catch((e) => {
              vm.templateError = e.userMessage;
            });
        };
        let operationId;
        let signature;
        vm.$onChanges = (changes) => {
          if (changes.conversationId) {
            if (dirty && changes.conversationId.previousValue) {
              drafts.save(changes.conversationId.previousValue, 'EXTERNAL', vm.text).catch(() => {});
            }
            dirty = false;
            generation++;
            $timeout.cancel(draftTimer);
            vm.text = '';
            vm.suggestions = [];
            vm.templateError = '';
            const token = generation;
            drafts
              .load(vm.conversationId, 'EXTERNAL')
              .then((result) => {
                if (token === generation && !vm.text) {
                  vm.text = result.content;
                }
              })
              .catch(() => {
                vm.draftError = 'Não foi possível carregar o rascunho.';
              });
          }
        };
        vm.submit = () => {
          if (vm.blocked || vm.sending || !vm.text.trim()) {
            return;
          }
          const content = vm.text;
          const currentSignature = JSON.stringify([vm.conversationId, content, actions.state.reply?.id]);
          if (signature !== currentSignature) {
            operationId = crypto.randomUUID();
            signature = currentSignature;
          }
          const conversationId = vm.conversationId;
          bus.emit(events.SEND_REQUESTED, {
            conversationId: vm.conversationId,
            content,
            operationId,
            reply: actions.state.reply,
            onSuccess: () => {
              if (vm.conversationId === conversationId && vm.text === content) {
                dirty = false;
                $timeout.cancel(draftTimer);
                vm.text = '';
                drafts.save(conversationId, 'EXTERNAL', '').catch(() => {
                  vm.draftError = 'Não foi possível limpar o rascunho sincronizado.';
                });
                actions.state.reply = null;
                signature = null;
              }
            },
          });
        };
        const off = bus.on('message.reuse', (command) => {
          if (command.conversationId === vm.conversationId) {
            vm.text = command.text;
            actions.state.reply = null;
          }
        });
        $scope.$on('$destroy', () => {
          generation++;
          $timeout.cancel(draftTimer);
          if (dirty && vm.conversationId) {
            drafts.save(vm.conversationId, 'EXTERNAL', vm.text).catch(() => {});
          }
          off();
        });
        vm.keydown = (event) => {
          if (vm.suggestions.length && ['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(event.key)) {
            event.preventDefault();
            if (event.key === 'Escape') {
              vm.suggestions = [];
            } else if (event.key === 'Enter') {
              vm.insertTemplate(vm.suggestions[vm.suggestionIndex]);
            } else {
              vm.suggestionIndex =
                (vm.suggestionIndex + (event.key === 'ArrowDown' ? 1 : -1) + vm.suggestions.length) %
                vm.suggestions.length;
            }
            return;
          }
          if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            vm.submit();
          }
        };
      },
    ],
  };
});
