import angular from 'angular';
import template from '../templates/app-modal.html?raw';

angular.module('wxn.core').directive('appModal', [
  function () {
    return {
      restrict: 'E',
      scope: {},
      transclude: true,
      bindToController: { open: '<', title: '@', busy: '<', onClose: '&' },
      controllerAs: '$modal',
      template,
      controller: function () {
        this.titleId = 'dialog-' + crypto.randomUUID();
        this.close = () => {
          if (!this.busy) {
            this.onClose();
          }
        };
        this.backdrop = (event) => {
          if (event.target === event.currentTarget) {
            this.close();
          }
        };
      },
    };
  },
]);
