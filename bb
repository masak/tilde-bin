#! /usr/local/bin/perl6

constant BOLD = "\e[1m";
constant BOLD_OFF = "\e[22m";

my %attrs =
    reset      => "0",
    bold       => "1",
    underline  => "4",
    inverse    => "7",
    black      => "30",
    red        => "31",
    green      => "32",
    yellow     => "33",
    blue       => "34",
    magenta    => "35",
    cyan       => "36",
    white      => "37",
    default    => "39",
    on_black   => "40",
    on_red     => "41",
    on_green   => "42",
    on_yellow  => "43",
    on_blue    => "44",
    on_magenta => "45",
    on_cyan    => "46",
    on_white   => "47",
    on_default => "49";

sub color (Str $what) is export {
    return ""
        if $what eq "";
    my @res;
    my @a = $what.split(' ');
    for @a -> $attr {
        if %attrs{$attr}:exists {
            @res.push: %attrs{$attr}
        } elsif $attr ~~ /^ ('on_'?) (\d+ [ ',' \d+ ',' \d+ ]?) $/ {
            @res.push: ~$0 ?? '48' !! '38';
            my @nums = $1.split(',');
            die("Invalid color value $_") unless +$_ <= 255 for @nums;
            @res.push: @nums == 3 ?? '2' !! '5';
            @res.append: @nums;
        } else {
            die("Invalid attribute name '$attr'")
        }
    }
    return "\e[" ~ @res.join(';') ~ "m";
}

sub colored (Str $what, Str $how) is export {
    color($how) ~ $what ~ color('reset');
}

my $name-column-width = 10;
my $master = qx[pwd].trim ~~ / « rakudo $ / ?? "nom" !! "master";

class Branch {
    has Str $.name;
    has Bool $.is-master;
    has Bool $.is-current;
    has Bool $.is-orphaned;
    has Int $.ahead;
    has Int $.behind;
    has Int $.unix-timestamp;
    has Str $.relative-time-ago;

    method gist() {
        my $description = do {
            when $.is-master { "" }
            when $.is-orphaned { "(orphaned)" }
            when !$.ahead && !$.behind { "(in sync with {$master})" }
            when !$.ahead { "($.behind behind)" }
            when !$.behind { "($.ahead ahead)" }
            default { "($.ahead ahead, $.behind behind)" }
        };
        my $color = do {
            when $.is-current && !$.ahead && !$.behind { "bold white on_black" }
            when $.is-current && !$.behind { "bold white on_green" }
            when $.is-current && !$.ahead { "bold white on_black" }
            when $.is-current { "bold white on_cyan" }
            when $.is-master { "" }
            when $.is-orphaned { "" }
            when !$.ahead && !$.behind { "" }
            when !$.behind { "bold green" }
            when !$.ahead { "bold black" }
            default { "bold cyan" }
        };
        return colored($.name, $color)
            ~ (" " x ($name-column-width - $.name.chars))
            ~ " $description".fmt("%-25s")
            ~ $.relative-time-ago.fmt("%20s");
    }
}

my @branches = qx[git branch --no-color].lines.map: {
    my $is-current = .substr(0, 1) eq "*";
    my $name = .subst(/../, "");
    next if $name ~~ /^ '('/;
    my $ahead = +qqx[git log --oneline {$master}..{$name} | wc -l].trim;
    my $behind = +qqx[git log --oneline {$name}..{$master} | wc -l].trim;
    my $is-master = $name eq $master;
    my $is-orphaned = qqx[git merge-base {$master} {$name}].lines == 0;
    my $unix-timestamp = qqx[git log -1 --pretty="%ct" {$name}].trim.Int;
    my $relative-time-ago = qqx[git log -1 --pretty="%cr" {$name}].trim;
    Branch.new(
        :$name,
        :$is-master,
        :$is-current,
        :$is-orphaned,
        :$ahead,
        :$behind,
        :$unix-timestamp,
        :$relative-time-ago
    );
}

$name-column-width max= @branches.map(*.name.chars).max;

.say for @branches.sort(-*.unix-timestamp)\
    .sort(*.is-orphaned)\
    .sort(*.is-master);

if @branches.grep({ !.ahead && .behind }) {
    say "";
    say "Use `git purge` to remove merged branches.";
}
