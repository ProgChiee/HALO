export function studentGreeting(user, workflow, loading = false) {
  if (loading || (!workflow.acting && !user)) return { title: 'Student Dashboard', subtitle: '' };
  const name = (workflow.target ?? user)?.name?.trim() || '';
  const firstName = name.split(/\s+/)[0] || 'there';
  if (workflow.acting) return { title: `Student Dashboard — ${name || 'Student'}`, subtitle: 'Learning overview' };
  const first = user.firstLogin === true;
  const prefix = first ? 'Welcome to HALO' : user.firstLogin === false ? 'Welcome back' : 'Welcome';
  return {
    title: `${prefix}, ${firstName}! 👋`,
    subtitle: first ? "Let's get started with your learning journey." : "Here's your learning overview for today.",
  };
}
