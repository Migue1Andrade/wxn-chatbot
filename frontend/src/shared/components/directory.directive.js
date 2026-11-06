import angular from 'angular';
import pagination from './directory-pagination.html?raw';
import menu from './action-menu.html?raw';
import skeleton from './table-skeleton.html?raw';
angular
  .module('wxn.core')
  .directive('directoryPagination', function () {
    return {
      restrict: 'E',
      scope: {},
      bindToController: { page: '=', total: '<', pageSize: '<', onChange: '&' },
      controllerAs: '$ctrl',
      template: pagination,
      controller: function () {
        this.pages = () => Math.max(1, Math.ceil(this.total / this.pageSize));
        this.change = (delta) => {
          this.page = Math.max(0, Math.min(this.pages() - 1, this.page + delta));
          this.onChange({ page: this.page });
        };
      },
    };
  })
  .directive('tableSkeleton', function () {
    return { restrict: 'E', template: skeleton };
  })
  .directive('actionMenu', function () {
    return {
      restrict: 'E',
      scope: {},
      transclude: true,
      bindToController: { label: '@' },
      controllerAs: '$ctrl',
      template: menu,
      controller: [
        '$scope',
        '$element',
        '$timeout',
        function ($scope, $element, $timeout) {
          let timer;
          this.close = (restore = false) => {
            this.open = false;
            if (restore) {
              $element[0].querySelector('button').focus();
            }
          };
          this.toggle = () => {
            this.open = !this.open;
            this.upward = false;
            if (this.open) {
              timer = $timeout(() => {
                const menu = $element[0].querySelector('.action-menu-items');
                this.upward = menu.getBoundingClientRect().bottom > window.innerHeight - 12;
                menu.querySelector('button')?.focus();
              }, 0);
            }
          };
          const outside = (event) => {
            if (this.open && !$element[0].contains(event.target)) {
              $scope.$evalAsync(this.close);
            }
          };
          const keydown = (event) => {
            if (event.key === 'Escape' && this.open) {
              event.stopPropagation();
              $scope.$evalAsync(() => {
                this.close();
                $element[0].querySelector('button').focus();
              });
            }
          };
          document.addEventListener('click', outside);
          $element.on('keydown', keydown);
          $scope.$on('$destroy', () => {
            $timeout.cancel(timer);
            document.removeEventListener('click', outside);
            $element.off('keydown', keydown);
          });
        },
      ],
    };
  });
