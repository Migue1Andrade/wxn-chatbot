export const ConversationFilters = Object.freeze([
  { value: 'all', label: 'Todas' },
  { value: 'pending', label: 'Pendentes' },
  { value: 'active', label: 'Em atendimento' },
  { value: 'resolved', label: 'Resolvidas' },
  { value: 'unassigned', label: 'Não atribuídas' },
  { value: 'closed', label: 'Encerradas' },
]);
const statuses = Object.freeze({
  pending: 'PENDING',
  active: 'IN_PROGRESS',
  resolved: 'RESOLVED',
  closed: 'CLOSED',
});
const statusLabels = Object.freeze({
  PENDING: 'Aguardando atendimento',
  IN_PROGRESS: 'Em atendimento',
  RESOLVED: 'Resolvida',
  CLOSED: 'Encerrada',
});
export const isConversationFinal = (status) => ['CLOSED', 'RESOLVED', 'CANCELLED'].includes(status);
export const visibleConversationStatus = (status) =>
  ({ WAITING_CUSTOMER: 'IN_PROGRESS', CANCELLED: 'CLOSED' })[status] || status;
export const conversationStatusLabel = (status) =>
  statusLabels[visibleConversationStatus(status)] || 'Status indisponível';

export function waitingForReply(conversation) {
  if (visibleConversationStatus(conversation?.status) !== 'IN_PROGRESS') {
    return '';
  }
  const inbound = Date.parse(conversation.lastInboundAt);
  const outbound = Date.parse(conversation.lastOutboundAt);
  const sentMostRecently =
    outbound > inbound ||
    !Number.isFinite(inbound) ||
    (outbound === inbound &&
      Date.parse(conversation.lastMessageAt) === outbound &&
      conversation.lastMessageDirection === 'OUTBOUND');
  if (Number.isFinite(outbound) && sentMostRecently) {
    return 'Aguardando o cliente';
  }
  return Number.isFinite(inbound) ? 'Aguardando o atendente' : '';
}

export function queryParameters(state) {
  const query = { search: state.search.trim() || undefined };
  if (state.readerId) {
    query.readerMemberId = state.readerId;
  }
  if (state.scope !== 'mine' && state.responsibleId) {
    query.assignedMemberId = state.responsibleId;
  }
  if (state.scope === 'mine' || state.filter === 'mine') {
    query.assignedMemberId = state.readerId;
  }
  if (state.scope !== 'mine' && state.filter === 'unassigned') {
    query.unassigned = true;
  }
  if (statuses[state.filter]) {
    query.status = statuses[state.filter];
  }
  return query;
}
export function matchesConversation(conversation, state) {
  if (
    (state.scope === 'mine' || state.filter === 'mine') &&
    (!state.readerId || conversation.assignedMemberId !== state.readerId)
  ) {
    return false;
  }
  if (state.scope !== 'mine' && state.filter === 'unassigned' && conversation.assignedMemberId != null) {
    return false;
  }
  if (
    state.scope !== 'mine' &&
    state.responsibleId &&
    conversation.assignedMemberId !== state.responsibleId
  ) {
    return false;
  }
  if (statuses[state.filter] && visibleConversationStatus(conversation.status) !== statuses[state.filter]) {
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
