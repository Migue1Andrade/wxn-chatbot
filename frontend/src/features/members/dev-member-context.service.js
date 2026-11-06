import angular from 'angular';
angular.module('wxn.members').factory('DevMemberContextService', [
  'MembersService',
  'AccountSessionState',
  '$q',
  'EventBusService',
  'MemberEvents',
  function (members, account, $q, bus, events) {
    const state = { member: null, members: [], availableHumans: [], loading: false, error: null };
    let pending = null;
    let pendingOwner;
    let generation = 0;
    function choose(id) {
      if (Number(id) !== account.user?.memberId) {
        return;
      }
      state.member = state.availableHumans.find((m) => m.id === Number(id)) || null;
      if (state.member) {
        sessionStorage.setItem('wxn.dev.member', String(state.member.id));
      }
      state.error = state.member ? null : 'Nenhum atendente humano ativo cadastrado para atendimento.';
      bus.emit(events.UPDATED, state.member);
    }
    function load() {
      if (pending && pendingOwner === account.user?.id) {
        return pending;
      }
      state.loading = true;
      const owner = account.user?.id;
      pendingOwner = owner;
      const token = ++generation;
      pending = $q
        .when(members.listMembers())
        .then((rows) => {
          if (owner !== account.user?.id || token !== generation) {
            return null;
          }
          state.members = rows.map((member) => ({
            ...member,
            available:
              member.status === 'ACTIVE' && (member.type === 'BOT' || member.accountStatus === 'ACTIVE'),
          }));
          state.availableHumans = rows.filter(
            (m) =>
              m.type === 'HUMAN' &&
              m.status === 'ACTIVE' &&
              m.accountStatus === 'ACTIVE' &&
              (account.user?.role === 'ADMIN' || m.id === account.user?.memberId),
          );
          const preferred =
            account.user?.role === 'ATTENDANT'
              ? account.user.memberId
              : account.user?.memberId ||
                state.member?.id ||
                Number(sessionStorage.getItem('wxn.dev.member'));
          const next =
            state.availableHumans.find((m) => m.id === preferred) || state.availableHumans[0] || null;
          const changed = state.member?.id !== next?.id;
          state.member = next;
          state.error = next ? null : 'Nenhum atendente humano ativo cadastrado para atendimento.';
          if (changed) {
            bus.emit(events.UPDATED, next);
          }
          return next;
        })
        .catch((error) => {
          if (token !== generation) {
            return;
          }
          state.member = null;
          state.members = [];
          state.availableHumans = [];
          state.error = error.userMessage;
        })
        .finally(() => {
          if (token !== generation) {
            return;
          }
          state.loading = false;
          pending = null;
        });
      return pending;
    }
    return { state, load, choose };
  },
]);
