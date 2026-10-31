-- Classement mondial de Sautique.
-- A coller dans Supabase : SQL Editor > New query > Run.

create table if not exists public.players (
  id uuid primary key,
  pseudo text not null check (char_length(pseudo) between 1 and 16),
  best integer not null default 0 check (best between 0 and 100000),
  updated_at timestamptz not null default now()
);

create index if not exists players_best_idx on public.players (best desc, updated_at asc);

-- Tout le monde peut lire le classement, personne ne peut écrire directement.
alter table public.players enable row level security;
drop policy if exists "lecture publique" on public.players;
create policy "lecture publique" on public.players for select using (true);

-- Seul moyen d'écrire : cette fonction, qui garde toujours le meilleur score.
create or replace function public.submit_score(p_id uuid, p_pseudo text, p_score integer)
returns void
language sql
security definer
set search_path = public
as $$
  insert into players (id, pseudo, best)
  values (p_id, trim(p_pseudo), greatest(p_score, 0))
  on conflict (id) do update
    set pseudo = excluded.pseudo,
        best = greatest(players.best, excluded.best),
        updated_at = now();
$$;

revoke all on function public.submit_score(uuid, text, integer) from public;
grant execute on function public.submit_score(uuid, text, integer) to anon, authenticated;
