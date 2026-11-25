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
angular.module('wxn.messages').directive('messageBubble', [
  '$timeout',
  function ($timeout) {
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
          this.menuOpensUp = false;
          this.positionMenu = (trigger) => {
            $timeout(() => {
              if (!this.showMenu || actions.state.menuMessageId !== this.message.id) {
                return;
              }
              const menu = trigger.parentElement.querySelector('.message-actions-menu');
              const viewport = trigger.closest('.message-viewport');
              if (!menu) {
                return;
              }

              const triggerRect = trigger.getBoundingClientRect();
              const visibleArea = viewport
                ? viewport.getBoundingClientRect()
                : { top: 0, bottom: window.innerHeight };
              const spaceAbove = Math.max(0, triggerRect.top - visibleArea.top - 8);
              const spaceBelow = Math.max(0, visibleArea.bottom - triggerRect.bottom - 8);
              const desiredHeight = Math.min(430, menu.scrollHeight);
              this.menuOpensUp = spaceBelow < desiredHeight && spaceAbove >= spaceBelow;

              const availableSpace = this.menuOpensUp ? spaceAbove : spaceBelow;
              const visibleTop = Math.max(8, visibleArea.top + 8);
              const visibleBottom = Math.min(window.innerHeight - 8, visibleArea.bottom - 8);
              const menuWidth = menu.offsetWidth;
              const minimumLeft = Math.max(8, visibleArea.left + 8);
              const maximumLeft = Math.min(
                window.innerWidth - menuWidth - 8,
                visibleArea.right - menuWidth - 8,
              );
              const preferredLeft = triggerRect.right - menuWidth;
              const left = Math.max(minimumLeft, Math.min(preferredLeft, maximumLeft));

              menu.style.maxHeight = `${Math.max(1, Math.min(430, availableSpace))}px`;
              menu.style.left = `${left}px`;
              menu.style.right = 'auto';

              const menuHeight = menu.offsetHeight;
              const preferredTop = this.menuOpensUp
                ? triggerRect.top - menuHeight - 8
                : triggerRect.bottom + 8;
              const maximumTop = Math.max(visibleTop, visibleBottom - menuHeight);
              const top = Math.max(visibleTop, Math.min(preferredTop, maximumTop));
              menu.style.top = `${top}px`;
              menu.style.bottom = 'auto';
            });
          };
          this.openMenu = (event) => {
            event.stopPropagation();
            const trigger = event.currentTarget;
            this.showMenu = !(this.showMenu && actions.state.menuMessageId === this.message.id);
            actions.state.menuMessageId = this.showMenu ? this.message.id : null;
            if (this.showMenu) {
              this.positionMenu(trigger);
            }
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
  },
]);
