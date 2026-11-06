import angular from 'angular';
import { contactInitials, contactTone } from '../../../shared/contact-identity.js';
import template from '../templates/message-bubble.html?raw';
const labels = {
  PENDING: 'Pendente',
  ACCEPTED: 'Aceita pelo provedor',
  SENT: 'Enviada',
  DELIVERED: 'Entregue',
  READ: 'Lida',
  FAILED: 'Falha no envio',
  SIMULATED: 'Simulada · Mock',
  UNKNOWN: 'Status desconhecido',
};
angular.module('wxn.messages').directive('messageBubble', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { message: '<', memberName: '@', contactName: '@' },
    controllerAs: '$ctrl',
    template,
    controller: [
      'MessageActionsService',
      function (actions) {
        this.actions = actions;
        this.showMenu = false;
        this.openMenu = (event) => {
          event.stopPropagation();
          const width = 220;
          const height = Math.min(430, window.innerHeight - 16);
          this.menuStyle = {
            position: 'fixed',
            maxHeight: height + 'px',
            overflowY: 'auto',
            left: Math.max(8, Math.min(event.clientX, window.innerWidth - width - 8)) + 'px',
            top: Math.max(8, Math.min(event.clientY, window.innerHeight - height - 8)) + 'px',
          };
          this.showMenu = !(this.showMenu && actions.state.menuMessageId === this.message.id);
          actions.state.menuMessageId = this.message.id;
        };
        this.act = (name, intent) => {
          this.showMenu = false;
          if (name === 'remove') {
            actions.remove(this.message, intent);
          } else {
            actions[name](this.message);
          }
        };
        this.locate = () => {
          const original = document.querySelector(
            '[data-message-id="' + this.message.replyToMessageId + '"]',
          );
          if (original) {
            original.scrollIntoView({ behavior: 'smooth', block: 'center' });
          } else {
            actions.state.notice =
              'Mensagem original não carregada. Carregue mensagens anteriores para localizá-la.';
          }
        };
        this.initials = contactInitials;
        this.tone = contactTone;
        this.statusLabel = () => labels[this.message.status] || this.message.status;
      },
    ],
    link(scope, element, _attributes, ctrl) {
      function close(event) {
        if (event.type === 'keydown' && event.key !== 'Escape') {
          return;
        }
        if (event.type === 'click' && element[0].contains(event.target)) {
          return;
        }
        if (ctrl.showMenu) {
          scope.$evalAsync(() => {
            ctrl.showMenu = false;
          });
        }
      }
      document.addEventListener('click', close);
      document.addEventListener('keydown', close);
      window.addEventListener('resize', close);
      document.addEventListener('scroll', close, true);
      scope.$on('$destroy', () => {
        document.removeEventListener('click', close);
        document.removeEventListener('keydown', close);
        window.removeEventListener('resize', close);
        document.removeEventListener('scroll', close, true);
      });
    },
  };
});
