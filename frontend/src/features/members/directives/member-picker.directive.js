import angular from 'angular';
import template from '../templates/member-picker.html?raw';
angular.module('wxn.members').directive('memberPicker', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { members: '<', selected: '=', disabled: '<' },
    controllerAs: '$picker',
    template,
    controller: function () {},
  };
});
