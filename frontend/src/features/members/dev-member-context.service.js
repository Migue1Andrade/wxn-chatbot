import angular from 'angular';
angular.module('wxn.members').factory('DevMemberContextService', [
  'MembersService',
  'AccountSessionState',
  '$q',
  'EventBusService',
  'MemberEvents',
  function (members, account, $q, bus, events) {
    const state = { member: null, members: [], loading: false, error: null };
    let pending = null;
    let pendingOwner;
    let generation = 0;
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
          const next =
            state.members.find(
              (member) => member.id === account.user?.memberId && member.type === 'HUMAN' && member.available,
            ) || null;
          const changed = state.member?.id !== next?.id;
          state.member = next;
          state.error = next ? null : 'Sua conta não está vinculada a um atendente ativo.';
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
    return { state, load };
  },
]);
