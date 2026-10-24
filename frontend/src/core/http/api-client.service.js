import angular from 'angular';

angular.module('wxn.core').factory('ApiClientService', [
  '$http',
  'environment',
  function ($http, environment) {
    function get(path, params) {
      return $http
        .get(environment.apiBaseUrl + '/api/companies/' + environment.companyId + path, { params })
        .then((r) => r.data);
    }
    function allPages(path, params = {}) {
      const result = [];
      function load(page) {
        return get(path, { ...params, page, size: 200 }).then((rows) => {
          result.push(...rows);
          return rows.length === 200 ? load(page + 1) : result;
        });
      }
      return load(0);
    }
    return {
      get,
      allPages,
      patch(path, body) {
        return $http
          .patch(environment.apiBaseUrl + '/api/companies/' + environment.companyId + path, body)
          .then((r) => r.data);
      },
      post(path, body) {
        return $http
          .post(environment.apiBaseUrl + '/api/companies/' + environment.companyId + path, body)
          .then((r) => r.data);
      },
    };
  },
]);
