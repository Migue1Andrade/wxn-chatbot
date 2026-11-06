export const ConversationFilters = Object.freeze([
  { value: 'all', label: 'Todas' },
  { value: 'pending', label: 'Pendentes' },
  { value: 'active', label: 'Em atendimento' },
  { value: 'mine', label: 'Minhas' },
  { value: 'unassigned', label: 'Não atribuídas' },
  { value: 'closed', label: 'Finalizadas' },
]);
const statuses = Object.freeze({ pending: 'PENDING', active: 'IN_PROGRESS', closed: 'CLOSED' });
export function queryParameters(state) {
  const query = { search: state.search.trim() || undefined };
  if (state.readerId) {
    query.readerMemberId = state.readerId;
  }
  if (state.responsibleId) {
    query.assignedMemberId = state.responsibleId;
  }
  if (state.filter === 'mine') {
    query.assignedMemberId = state.readerId;
  }
  if (state.filter === 'unassigned') {
    query.unassigned = true;
  }
  if (statuses[state.filter]) {
    query.status = statuses[state.filter];
  }
  return query;
}
export function matchesConversation(conversation, state) {
  if (state.filter === 'mine' && (!state.readerId || conversation.assignedMemberId !== state.readerId)) {
    return false;
  }
  if (state.filter === 'unassigned' && conversation.assignedMemberId != null) {
    return false;
  }
  if (state.responsibleId && conversation.assignedMemberId !== state.responsibleId) {
    return false;
  }
  if (statuses[state.filter] && conversation.status !== statuses[state.filter]) {
    return false;
  }
  const search = state.search.trim().toLocaleLowerCase();
  if (!search) {
    return true;
  }
  const phone = search.replace(/[ ()-]/g, '');
  return (
    (conversation.displayName || '').toLocaleLowerCase().includes(search) ||
    (phone.length > 0 && (conversation.contactPhone || '').includes(phone)) ||
    (conversation.externalChatId || '').toLocaleLowerCase().includes(search) ||
    conversation.id.toLocaleLowerCase() === search
  );
}
