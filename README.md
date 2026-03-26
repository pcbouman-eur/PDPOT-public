# Pickup and Delivery Problem with Online Transfers (PDPOT)

<!-- Include animated gif here -->

## Introduction

This is a repository with instances, solutions and code for the Pickup and Delivery Problem with Online Transfers (PDPOT).
Currently, the code does not contain our main solvers, but these may be added at a later moment when our paper is accept for publication.

The Pick and Delivery Problem with Online Transfers is inspired by prototype vehicles from [Next future mobiility](https://www.youtube.com/watch?v=99XH1vbn4Xk)
An instance has a road network, with parking locations, stop locations, and crossing location.
The demand set contains passenger requests with and origin and destination.
The supply set contains modular vehicles that have a seat capacity.
Time is discretized into time units, and when multiple vehicles travel the same road in the same direction at the same time,
passengers can perform *inside transfers* between those vehicles while they are driving.
Alternatively, passengers can leave a vehicle to perform an *outside transfer*, but leaving or entering a vehicle always costs at least one time unit.
The main objective of the problem is to service as many passengers as possible, with additional objectives to bring passengers to their destinations as soon as possible and to minimize the distance the vehicles drive (with a small 'platooning' bonus if they drive together).
We solve the problem in two phases: first we determine the routes and times for both vehicles and passengers, ensuring that there is sufficient capacity in all vehicles to service the selected passenger requests. Then, we determine an exact seat assignment based on the routes and times, minimizing the number of inside transfers for the serviced passengers.

## Instances

Our instances can be found in [/data/instances](/data/instances).

The road networks in our instances are based on artificially generated road networks, which were generated with an approach due to [Courtat et al.](https://doi.org/10.1103/PhysRevE.83.036106), capturing a snapshot during the generation process at either the 8th, 10th, 12th, 15th or 20th iteration of the process. This way we obtain networks of five different network sizes.

| ![Diagram showing ten networks each with 5 different size variants](/docs/networks.png) |
|:--:|
| The ten different networks we used, each with 5 size variants. The colors show which roads are added with increasing network size for each of the networks. |

The demand and supply sets in our instances contain either 10 or 15 passenger requests (small instances), 2 or 3 vehicles (small instances), 50 or 60 passenger requests (large instances), and 10 or 12 vehicles (large instances). All vehicles have seat capacity 4. There 600 small instances in total, and 600 large instances in total.
Instances for which the supply or demand set is a subset of those of another instance are linked by a `derivedFrom` metadata-field. This allows to measure the pairwise impact of increasing or decreasing the demand and/or supply set of an instance.
Additionally, for each instance there is a `forbid` counterpart, where it is not allowed to use any *inside transfers* at all. This way, we can perform pairwise comparisons of allowing inside transfers, compared to a more traditional setup with shared taxis.

All instances are stored as `.json` files. Each individual file contains all necessary data for the instance: the road network, the set of passenger requests, the set of vehicles, the parameters and settings related to the objective, and possibly meta-data. Each instance also has a [universally unique identiefier (UUID)](https://en.wikipedia.org/wiki/Universally_unique_identifier) stored in it's metadata, so it can be easily identified.
The exact structure of our instances is defined in our Java code, mapped using [Jackson annotations](https://github.com/FasterXML/jackson-databind). See [/java/pdpot-core/src/main/java/podrouting/data/Instance.java](/java/pdpot-core/src/main/java/podrouting/data/Instance.java) for details.

## Solutions

In our research we implemented to solution methods for this problem (these are currently not included in this repository): a full MIP formulation that can be used to solved the problem directly, and a column generation approach that is a root-node heuristic based on generating routes for both vehicles and passenger requests.
Our solutions can be found in six zip-files stored in [/data/solutions](/data/solutions):

- `small-regular-mip.zip`: contains solutions from the direct MIP solver for the small instances
- `small-regular-colgen.zip`: contains solutions from the column generation solver for the small instances
- `small-forbid-mip.zip`: contains solutions from the direct MIP solver for the small instances where inside transfers are not allowed
- `large-regular-mip.zip`: contains solutions from the direct MIP solver for the large instances
- `large-regular-colgen.zip`: contains solutions from the column generation solver for the large instances
- `large-forbid-mip.zip`: contains solutions from the direct MIP solver for the large instances where inside transfers are not allowed

The solutions were computed on [Snellius](https://www.surf.nl/en/services/compute/snellius-the-national-supercomputer), the Dutch National Supercomputer. For each solution, the is a `.log` file that contains the output of the solution run, and a `.json` file with the actual solution.

The `.json` solution files are self contained: they contain the full data of the `instance` they are a solution for, they contain paths for all serviced passenger requests and for the vehicles, and they contain relevant meta-data.
The exact structure of our solutions is defined in our Java code, mapped using [Jackson annotations](https://github.com/FasterXML/jackson-databind). See [/java/pdpot-core/src/main/java/podrouting/data/Solution.java](/java/pdpot-core/src/main/java/podrouting/data/Solution.java) for details.

Note that our solutions do not contain the exact *seat assignment*: our models only enforce that there is sufficient capacity. The Java code in this capacity, in particular the `pdpot-assign` module contains a model that minimizes the number of inside transfers by assigning vehicles and seats to passengers for every step of their routes.


## Use of AI

The Java code and experimental data were written manually without help from AI. The Python scripts for data processing were produced with extensive help of a qwen3-coder-next model called from a Cline plugin, rewriting some original dirty handcrafted scripts. The scripts to run replications and install commands were created with help from AI.

## Licensing

* The [Java code](/java) in this repository is licensed with the GNU Affero General Public License version 3.
* The [instance and solution data](/data) in this repository is licensed with the Creative Commons Attribution-ShareAlike 4.0 International License
* The [scripts for data processing and analysis](/script) in this repository is licensed with the Apache License version 2.0
