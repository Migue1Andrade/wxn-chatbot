import angular from 'angular';
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
    bindToController: { message: '<', memberName: '@' },
    controllerAs: '$ctrl',
    template,
    controller: function () {
      this.statusLabel = () => labels[this.message.status] || this.message.status;
    },
  };
});
