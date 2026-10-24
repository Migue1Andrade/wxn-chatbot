import angular from 'angular';
angular.module('wxn.members').factory('DevMemberContextService', [
  'MembersService',
  'UsersService',
  '$q',
  'EventBusService',
  'MemberEvents',
  'environment',
  function (members, users, $q, bus, events, environment) {
    const state = { member: null, members: [], availableHumans: [], loading: false, error: null };
    let pending = null;
    function choose(id) {
      state.member = state.availableHumans.find((m) => m.id === Number(id)) || null;
      if (state.member)
        sessionStorage.setItem('wxn.dev.member.' + environment.companyId, String(state.member.id));
      state.error = state.member ? null : 'Nenhum atendente humano ativo cadastrado nesta empresa.';
      bus.emit(events.UPDATED, state.member);
    }
    function load() {
      if (pending) return pending;
      state.loading = true;
      pending = $q
        .all([members.listMembers(), users.listUsers()])
        .then(([rows, humans]) => {
          state.members = rows.map((member) => ({
            ...member,
            available:
              member.status === 'ACTIVE' &&
              (member.type === 'BOT' || humans.some((user) => user.id === member.userId)),
          }));
          state.availableHumans = rows.filter(
            (m) => m.type === 'HUMAN' && m.status === 'ACTIVE' && humans.some((u) => u.id === m.userId),
          );
          const preferred =
            state.member?.id || Number(sessionStorage.getItem('wxn.dev.member.' + environment.companyId));
          const next =
            state.availableHumans.find((m) => m.id === preferred) || state.availableHumans[0] || null;
          const changed = state.member?.id !== next?.id;
          state.member = next;
          state.error = next ? null : 'Nenhum atendente humano ativo cadastrado nesta empresa.';
          if (changed) bus.emit(events.UPDATED, next);
          return next;
        })
        .catch((error) => {
          state.member = null;
          state.members = [];
          state.availableHumans = [];
          state.error = error.userMessage;
        })
        .finally(() => {
          state.loading = false;
          pending = null;
        });
      return pending;
    }
    return { state, load, choose };
  },
]);
