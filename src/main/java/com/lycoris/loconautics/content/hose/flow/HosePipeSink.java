package com.lycoris.loconautics.content.hose.flow;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

// pipes have no fluid handler, so we fill the open ends of the network
public final class HosePipeSink implements IFluidHandler {

    private record Step(BlockPos pos, int pipes) {
    }

    private final Level level;
    private final BlockPos endpoint;
    private final BlockPos firstPipe;

    public HosePipeSink(Level level, BlockPos endpoint, Direction plateSide) {
        this.level = level;
        this.endpoint = endpoint;
        this.firstPipe = endpoint.relative(plateSide);
    }

    public static boolean isPipe(Level level, BlockPos pos) {
        return FluidPropagator.getPipe(level, pos) != null;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int left = resource.getAmount();
        for (IFluidHandler end : openEnds()) {
            if (left <= 0) {
                break;
            }
            left -= end.fill(resource.copyWithAmount(left), action);
        }
        return resource.getAmount() - left;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }

    private java.util.List<IFluidHandler> openEnds() {
        java.util.List<IFluidHandler> ends = new java.util.ArrayList<>();
        Queue<Step> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        int limit = FluidPropagator.getPumpRange();
        queue.add(new Step(firstPipe, 1));
        seen.add(firstPipe);
        while (!queue.isEmpty()) {
            Step step = queue.poll();
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, step.pos());
            if (pipe == null) {
                continue;
            }
            for (Direction d : FluidPropagator.getPipeConnections(level.getBlockState(step.pos()), pipe)) {
                BlockPos next = step.pos().relative(d);
                if (next.equals(endpoint) || !level.isLoaded(next) || !seen.add(next)) {
                    continue;
                }
                if (FluidPropagator.getPipe(level, next) != null) {
                    if (step.pipes() < limit) {
                        queue.add(new Step(next, step.pipes() + 1));
                    }
                    continue;
                }
                IFluidHandler handler = handlerAt(next, d.getOpposite());
                if (handler != null) {
                    ends.add(handler);
                }
            }
        }
        return ends;
    }

    @Nullable
    private IFluidHandler handlerAt(BlockPos pos, Direction side) {
        return level.getCapability(Capabilities.FluidHandler.BLOCK, pos, side);
    }
}
