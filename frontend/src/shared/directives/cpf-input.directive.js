import angular from 'angular';
import { cpfDigits, formatCpf, validCpf } from '../cpf.js';
angular
  .module('wxn.core')
  .directive('cpfInput', function () {
    return {
      restrict: 'A',
      require: 'ngModel',
      link(_scope, _element, _attributes, model) {
        model.$formatters.push(formatCpf);
        model.$parsers.unshift((value) => {
          const digits = cpfDigits(value).slice(0, 11);
          const formatted = formatCpf(digits);
          if (formatted !== value) {
            model.$setViewValue(formatted);
            model.$render();
          }
          return digits;
        });
        model.$validators.cpf = (value, view) => model.$isEmpty(view) || validCpf(value);
      },
    };
  })
  .filter('cpf', function () {
    return formatCpf;
  });
