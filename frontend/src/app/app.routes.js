import angular from 'angular';
import { environment } from '../core/config/environment.js';
angular.module('wxnDashboard').config([
  '$routeProvider',
  function ($routeProvider) {
    function page(template, admin = false) {
      return {
        template,
        resolve: {
          access: [
            'AccountSessionService',
            '$location',
            '$q',
            function (session, $location, $q) {
              return session
                .ensure()
                .then((user) => {
                  if (!user) {
                    $location.path('/login');
                    return $q.reject('LOGIN_REQUIRED');
                  }
                  if (admin && user.role !== 'ADMIN') {
                    $location.path('/');
                    return $q.reject('ADMIN_REQUIRED');
                  }
                  return user;
                })
                .catch((error) => {
                  if (error !== 'ADMIN_REQUIRED') {
                    $location.path('/login');
                  }
                  return $q.reject(error);
                });
            },
          ],
        },
      };
    }
    $routeProvider
      .when('/setup/whatsapp', { template: '<whatsapp-setup></whatsapp-setup>' })
      .when('/login', {
        template: '<account-login></account-login>',
        resolve: {
          setup: [
            'WhatsAppBootstrapService',
            '$location',
            '$q',
            function (bootstrap, $location, $q) {
              if (environment.skipWhatsAppSetup) {
                return true;
              }
              return bootstrap.status().then((status) => {
                if (
                  !environment.skipWhatsAppSetup &&
                  (!status.configured || (!status.completed && status.state !== 'CONNECTED'))
                ) {
                  $location.path('/setup/whatsapp');
                  return $q.reject('SETUP_REQUIRED');
                }
                return status;
              });
            },
          ],
        },
      })
      .when('/', {
        template: '<chat-layout></chat-layout>',
        reloadOnSearch: false,
        resolve: {
          access: page('<chat-layout></chat-layout>').resolve.access,
          setup: [
            'WhatsAppBootstrapService',
            '$location',
            '$q',
            function (bootstrap, $location, $q) {
              if (environment.skipWhatsAppSetup) {
                return true;
              }
              return bootstrap.status().then((status) => {
                if (
                  !environment.skipWhatsAppSetup &&
                  (!status.configured || (!status.completed && status.state !== 'CONNECTED'))
                ) {
                  $location.path('/setup/whatsapp');
                  return $q.reject('SETUP_REQUIRED');
                }
                return status;
              });
            },
          ],
        },
      })
      .when('/kanban', { ...page('<kanban-page></kanban-page>', true), reloadOnSearch: false })
      .when('/dashboard', page('<operation-dashboard></operation-dashboard>', true))
      .when('/settings/quick-replies', page('<quick-replies-page></quick-replies-page>'))
      .when('/settings/global-quick-replies', page('<quick-replies-page></quick-replies-page>', true))
      .when('/settings/channels', page('<channels-page></channels-page>', true))
      .when('/settings/sla', page('<sla-settings></sla-settings>', true))
      .when('/reports', page('<reports-page></reports-page>', true))
      .when('/tasks', page('<tasks-page></tasks-page>'))
      .when('/notifications', page('<notifications-page></notifications-page>'))
      .when('/settings', page('<settings-hub></settings-hub>'))
      .when('/settings/departments', page('<management-page></management-page>', true))
      .when('/members', page('<management-page></management-page>', true))
      .otherwise({ redirectTo: '/' });
  },
]);
